import net.kurobako.jmhv.JMHReport
import org.scalatest.prop.TableDrivenPropertyChecks.{forAll, _}
import org.scalatest.{EitherValues, FlatSpec, Matchers}

import scala.io.Source

class JMHReportSpec extends FlatSpec with Matchers with EitherValues{

	behavior of "JMHReport"


	it should "parse the sample json corpus correctly" in {


		forAll(Table(
			"input",
			"empty.json",
			"one.json",
			"data.json",
			"data2.json",
			"dataN.json",
			"dataN2.json",
			"sample1.json",
			"string-concatenation_jdk7.json",
			"string-concatenation_jdk8.json",
		)) { file =>
			// TODO need to verify whether data structure are actually equal
			JMHReport(Source.fromResource(file).mkString) match {
				case Left(value)  => fail(value)
				case Right(value) => // TODO validate this
			}
		}


	}


}
