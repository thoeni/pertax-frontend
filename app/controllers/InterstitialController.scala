/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import javax.inject.Inject

import config.ConfigDecorator
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.{AuthorisedActions, PertaxRegime}
import controllers.helpers.PaperlessInterruptHelper
import error.LocalErrorHandler
import models.Breadcrumb
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Request}
import play.twirl.api.Html
import services.partials.{FormPartialService, SaPartialService}
import services.{CitizenDetailsService, PreferencesFrontendService, UserDetailsService}
import uk.gov.hmrc.play.partials.HtmlPartial
import util.LocalPartialRetriever

import scala.concurrent.Future


class InterstitialController @Inject() (
  val messagesApi: MessagesApi,
  val formPartialService: FormPartialService,
  val saPartialService: SaPartialService,
  val citizenDetailsService: CitizenDetailsService,
  val userDetailsService: UserDetailsService,
  val delegationConnector: FrontEndDelegationConnector,
  val preferencesFrontendService: PreferencesFrontendService,
  val auditConnector: PertaxAuditConnector,
  val authConnector: PertaxAuthConnector,
  val partialRetriever: LocalPartialRetriever,
  val configDecorator: ConfigDecorator,
  val pertaxRegime: PertaxRegime,
  val localErrorHandler: LocalErrorHandler
) extends PertaxBaseController with AuthorisedActions with PaperlessInterruptHelper {

  val saBreadcrumb: Breadcrumb =
    "label.self_assessment" -> routes.InterstitialController.displaySelfAssessment().url ::
    baseBreadcrumb

  private def currentUrl(implicit request: Request[AnyContent]) =
    configDecorator.pertaxFrontendHost + request.path

  def displayNationalInsurance: Action[AnyContent] = ProtectedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      showingWarningIfWelsh { implicit pertaxContext =>
        formPartialService.getNationalInsurancePartial.map { p =>
          Ok(views.html.interstitial.viewNationalInsuranceInterstitialHome(formPartial = p successfulContentOrElse Html(""), redirectUrl = currentUrl))
        }
      }
  }

  def displayTaxCreditsSummary: Action[AnyContent] = ProtectedAction(baseBreadcrumb, fetchPersonDetails = false) {
    implicit pertaxContext =>
      showingWarningIfWelsh { implicit pertaxContext =>
        enforcePaperlessPreference {
          val (summaryPartial, iFormsPartial) = (
            if (configDecorator.taxCreditsEnabled) formPartialService.getTaxCreditsSummaryPartial.map(Some(_)) else Future.successful(None),
            if (configDecorator.taxCreditsIFormsEnabled) formPartialService.getTaxCreditsIFormsPartial.map(Some(_)) else Future.successful(None)
          )

          for (s <- summaryPartial; i <- iFormsPartial) yield {
            (s, i) match {
              case (Some(HtmlPartial.Failure(_, _)), Some(HtmlPartial.Failure(_, _))) =>
                Ok(views.html.interstitial.viewTaxCreditsSummaryPartialFailed())
              case (taxCreditsSummaryPartial, taxCreditsIFormsPartial) =>
                Ok(views.html.interstitial.viewTaxCreditsSummaryInterstitial(
                  taxCreditsSummaryPartial = taxCreditsSummaryPartial.fold(Html(""))(_.successfulContentOrEmpty),
                  taxCreditsIFormsPartial = taxCreditsIFormsPartial.fold(Html(""))(_.successfulContentOrEmpty),
                  redirectUrl = currentUrl,
                  egainWebchatPertaxId = configDecorator.egainWebchatPertaxId
                ))
            }
          }
        }
      }
  }

  def displayChildBenefits: Action[AnyContent] = ProtectedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      Future.successful(Ok(views.html.interstitial.viewChildBenefitsSummaryInterstitial(
        redirectUrl = currentUrl,
        taxCreditsEnabled = configDecorator.taxCreditsEnabled))
      )
  }

  def displaySelfAssessment: Action[AnyContent] = ProtectedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      showingWarningIfWelsh { implicit pertaxContext =>
        val formPartial = formPartialService.getSelfAssessmentPartial recoverWith {
          case e => Future.successful(HtmlPartial.Failure(None, ""))
        }
        val saPartial = saPartialService.getSaAccountSummary recoverWith {
          case e => Future.successful(HtmlPartial.Failure(None, ""))
        }

        enforceGovernmentGatewayUser {
          enforceSaUser {
            for {
              formPartial <- formPartial
              saPartial <- saPartial
            } yield {
              Ok(views.html.selfAssessmentSummary(
                formPartial successfulContentOrElse Html(""),
                saPartial successfulContentOrElse Html("")
              ))
            }
          }
        }
      }
  }

  def displaySa302Interrupt(year: Int): Action[AnyContent] = ProtectedAction(saBreadcrumb) {
    import util.DateTimeTools.previousAndCurrentTaxYearFromGivenYear
    implicit pertaxContext =>
      enforceSaAccount { saAccount =>
        Future.successful(Ok(views.html.selfassessment.sa302Interrupt(year = previousAndCurrentTaxYearFromGivenYear(year), saUtr = saAccount.utr)))
      }
  }
}
