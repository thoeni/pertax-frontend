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

package error

import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import config.ConfigDecorator
import connectors.{FrontEndDelegationConnector, PertaxAuthConnector}
import controllers.auth.{PertaxRegime, PublicActions}
import play.api.http.HttpErrorHandler
import play.api.http.Status._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{CitizenDetailsService, UserDetailsService}
import uk.gov.hmrc.play.filters.frontend.CookieCryptoFilter
import util.LocalPartialRetriever

import scala.concurrent._


@Singleton
class LocalErrorHandler @Inject() (
  val messagesApi: MessagesApi,
  val userDetailsService: UserDetailsService,
  val citizenDetailsService: CitizenDetailsService,
  val partialRetriever: LocalPartialRetriever,
  val configDecorator: ConfigDecorator,
  val pertaxRegime: PertaxRegime,
  val delegationConnector: FrontEndDelegationConnector,
  val authConnector: PertaxAuthConnector,
  val materializer: Materializer,
  val sessionCookieCryptoFilter: CookieCryptoFilter
) extends HttpErrorHandler with PublicActions with I18nSupport {


  def onClientError(request: RequestHeader, statusCode: Int, message: String) = {

    val errorKey = statusCode match {
      case BAD_REQUEST => "badRequest400"
      case NOT_FOUND => "pageNotFound404"
      case _ => "InternalServerError500"
    }

    PublicAction { implicit pertaxContext =>
      Future.successful(Status(statusCode)(views.html.error(
        s"global.error.$errorKey.title",
        Some(s"global.error.$errorKey.heading"),
        Some(s"global.error.$errorKey.message"))))
    }.apply(request).run()(materializer)

  }

  def onServerError(request: RequestHeader, exception: Throwable) =
    onClientError(request, INTERNAL_SERVER_ERROR, "")

}

