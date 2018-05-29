import net.kurobako.jmhv.JMHReport
import org.scalatest.prop.TableDrivenPropertyChecks.{forAll, _}
import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source

class JMHReportSpec extends FlatSpec with Matchers {

	behavior of "JMHReport"


	it should "parse the sample json corpus correctly" in {


		forAll(Table(
			"input",
			"data.json",
			"data2.json",
			"sample1.json",
			"string-concatenation_jdk7.json",
			"string-concatenation_jdk8.json",
		)) { file =>
			// TODO need to verify whether data structure are actually equal
			JMHReport(Source.fromResource(file).mkString).isRight shouldBe true
		}


	}


}
