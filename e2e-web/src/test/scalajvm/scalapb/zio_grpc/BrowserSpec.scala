package scalapb.zio_grpc

import zio.test._
import zio.test.Assertion._
import zio.test.Assertion._
import com.microsoft.playwright._
import zio.ZIO
import zio.Console._
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

object BrowserSpec extends ZIOSpecDefault {
  def spec =
    suite("BrowserSpec")(
      test("all tests should pass")(
        for {
          pw <- ZIO.fromAutoCloseable(ZIO.attemptBlocking(Playwright.create()))
          _  <- ZIO.attemptBlocking {
                  val browser  = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))
                  val page     = browser.newPage()
                  val htmlFile = java.nio.file.Paths.get("").toAbsolutePath().toString
                  page.navigate(s"file:///${htmlFile}/e2e-web/index.html")
                  assertThat(page.getByTestId("log")).containsText("0 tests failed")
                }
        } yield assertTrue(true)
      )
    )
}
