package scalapb.zio_grpc

import io.grpc.ServerBuilder
import zio.test._
import zio.test.Assertion._
import zio.test.Assertion._
import com.microsoft.playwright._
import zio.ZIO
import zio.Console._
import zio.ZLayer
import zio.Scope
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

object BrowserSpec extends ZIOSpecDefault {
  val serverLayer: ZLayer[Any, Throwable, Server] =
    ServerLayer.fromServiceLayer(ServerBuilder.forPort(9000))(server.TestServiceImpl.live)

  val playwright: ZLayer[Any, Throwable, Playwright] =
    ZLayer.scoped(ZIO.fromAutoCloseable(ZIO.attemptBlocking(Playwright.create())))

  def spec =
    suite("BrowserSpec")(
      test("all tests should pass")(
        for {
          pw <- ZIO.service[Playwright]
          _  <- ZIO.attemptBlocking {
                  val browser  = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false))
                  val page     = browser.newPage()
                  val htmlFile = java.nio.file.Paths.get("").toAbsolutePath().toString
                  page.navigate(s"file:///${htmlFile}/e2e-web/index.html")
                  assertThat(page.getByTestId("log")).containsText(". 0 tests failed")
                }
        } yield assertTrue(true)
      )
    ).provideLayer(serverLayer ++ playwright)
}
