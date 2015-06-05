package com.eharmony.matching.aloha.models.vw.jni

import java.io.File
import java.{lang => jl}

import com.eharmony.matching.aloha.FileLocations
import com.eharmony.matching.aloha.factory.JavaJsonFormats._
import com.eharmony.matching.aloha.factory.ModelFactory
import com.eharmony.matching.aloha.id.ModelId
import com.eharmony.matching.aloha.models.TypeCoercion
import com.eharmony.matching.aloha.reflect.RefInfo
import com.eharmony.matching.aloha.score.conversions.ScoreConverter
import com.eharmony.matching.aloha.score.conversions.ScoreConverter.Implicits._
import com.eharmony.matching.aloha.semantics.compiled.CompiledSemantics
import com.eharmony.matching.aloha.semantics.compiled.compiler.TwitterEvalCompiler
import com.eharmony.matching.aloha.semantics.compiled.plugin.csv.{CompiledSemanticsCsvPlugin, CsvLine, CsvLines, CsvTypes}
import com.eharmony.matching.aloha.semantics.func.{GenFunc, GeneratedAccessor}
import com.eharmony.matching.aloha.util.Logging
import org.junit.Assert._
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.{BeforeClass, Ignore, Test}
import spray.json.DefaultJsonProtocol._
import spray.json._
import vw.VWScorer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try


@RunWith(classOf[BlockJUnit4ClassRunner])
class VwJniModelTest {
    import VwJniModelTest._

    @Test def testByteOutputType(): Unit = testOutputType[Byte]()
    @Test def testShortOutputType(): Unit = testOutputType[Short]()
    @Test def testIntOutputType(): Unit = testOutputType[Int]()
    @Test def testLongOutputType(): Unit = testOutputType[Float]()
    @Test def testFloatOutputType(): Unit = testOutputType[Float]()
    @Test def testDoubleOutputType(): Unit = testOutputType[Double]()
    @Test def testStringOutputType(): Unit = testOutputType[String]()
    @Test def testJavaByteOutputType(): Unit = testOutputType[jl.Byte]()
    @Test def testJavaShortOutputType(): Unit = testOutputType[jl.Short]()
    @Test def testJavaIntegerOutputType(): Unit = testOutputType[jl.Integer]()
    @Test def testJavaLongOutputType(): Unit = testOutputType[jl.Long]()
    @Test def testJavaFloatOutputType(): Unit = testOutputType[jl.Float]()
    @Test def testJavaDoubleOutputType(): Unit = testOutputType[jl.Double]()

    @Test def testNoThreshWithMissing(): Unit = {
        val m = model[Float](noThreshJson)
        val y = m(missingHeight)
        assertTrue(y.isDefined)
    }

    @Test def testExceededThresh(): Unit = {
        val m = model[Float](threshJson)
        val y = m(missingHeight)
        assertTrue(y.isEmpty)
        assertEquals(List("height_cm"), m.featureFunctions.head.accessorOutputMissing(missingHeight))
    }

    /**
     * This should succeed.  It just logs when non-existent features are listed in the namespace.
     */
    @Test def testNsWithUndeclaredFeatureNames(): Unit = {
        val m = model[Float](nsWithUndeclFeatureJson)
    }

    /**
     * It's ok to have namespaces not cover all features.  The remainder goes into the default ns.
     */
    @Test def testNsDoesntCoverAllFeatureNamesFromJson(): Unit = {
        val m = model[Float](nonCoveringNsJson)
        val input = m.generateVwInput(missingHeight)
        assertEquals(Right("| height:180 "), input)
    }

    @Test(expected = classOf[IllegalArgumentException])
    def testNssAndDefaultDoesntCoverAllFeatureInd(): Unit = {
        val accessor = GeneratedAccessor("height_cm", (_:CsvLine).ol("height_cm"))
        val h = GenFunc.f1(accessor)("ind(height_cm * 10)", _.map(h => Seq(("height_mm=" + (h * 10), 1.0))).getOrElse(Nil))

        // Because height_mm feature isn't in any namespace.
        VwJniModel(
            ModelId.empty,
            new VWScorer(""),
            Vector("height_mm"),
            Vector(h),
            Nil,
            Nil,
            (f: Float) => f
        )
    }

    @Test(expected = classOf[IllegalArgumentException])
    def testNamesSizeLtFeaturesSize(): Unit = {
        val accessor = GeneratedAccessor("height_cm", (_:CsvLine).ol("height_cm"))
        val h = GenFunc.f1(accessor)("ind(height_cm * 10)", _.map(h => Seq(("height_mm=" + (h * 10), 1.0))).getOrElse(Nil))

        VwJniModel(
            ModelId.empty,
            new VWScorer(""),
            Vector(),
            Vector(h),
            Nil,
            Nil,
            (f: Float) => f
        )
    }

    @Test(expected = classOf[IllegalArgumentException])
    def testFeaturesSizeLtNamesSize(): Unit = {
        val accessor = GeneratedAccessor("height_cm", (_:CsvLine).ol("height_cm"))
        val h = GenFunc.f1(accessor)("ind(height_cm * 10)", _.map(h => Seq(("height_mm=" + (h * 10), 1.0))).getOrElse(Nil))

        VwJniModel(
            ModelId.empty,
            new VWScorer(""),
            Vector("height_mm"),
            Vector(),
            Nil,
            Nil,
            (f: Float) => f
        )
    }


    /**
     * Can't test.  Catching throwable or setting test annotation to @Test(expected = classOf[Error]) causes the
     * world to blow up.  Usually says: Invalid memory access of location 0x0 rip=[HEX HERE]
     */
    // TODO: Figure out how to test this.
    @Ignore @Test def testBadVwArgsThrowsEx(): Unit = {
        try {
            val m = model[Float](badVwArgsJson)
            fail()
        }
        catch {
            case e: Error =>  // Swallow.  We want exception.
        }
    }

    private[this] def model[A : RefInfo : ScoreConverter : JsonReader](modelJson: JsValue) = {
        val m = ModelFactory.defaultFactory.getModel[CsvLine, A](modelJson, Option(semantics)).get
        assertEquals("VwJniModel", m.getClass.getSimpleName)
        m.asInstanceOf[VwJniModel[CsvLine, A]]
    }

    private[this] def testOutputType[A : RefInfo : ScoreConverter : JsonReader](): Unit = {
        val tc = TypeCoercion[Float, Option[A]].get
        val m = model[A](typeTestJson)
        val y = m(missingHeight)
        assertEquals(tc(ExpVwOutput), y)
    }
}

object VwJniModelTest extends Logging {
    private[this] val VwModelFile = new File(FileLocations.testDirectory, "VwJniModelTest-vw.model")
    private val VwModelPath = VwModelFile.getCanonicalPath

    val columns = Seq(
        "height_cm" -> CsvTypes.LongOptionType,
        "weight" -> CsvTypes.IntType,
        "hair.color" -> CsvTypes.StringType
    )

    val featuresTestJson =
        """
          |{
          |  "modelType": "VwJNI",
          |  "modelId": { "id": 0, "name": "" },
          |  "numMissingThreshold": 0,
          |  "features": {
          |    "height_mm": { "spec": "${height_cm} * 10", "defVal": [["=UNK", 1]] },
          |    "height_cm": "${height_cm}",
          |    "weight": "ind(${weight} / 10)",
          |    "hair": { "spec": "ind(${hair.color})" }
          |  },
          |  "namespaces": {
          |    "personal_features": [ "height_mm", "weight", "hair" ]
          |  },
          |  "vw": {
          |    "params": [
          |      "--quiet",
          |      "-t"
          |    ]
          |  }
          |}
        """.stripMargin.trim.parseJson

    val typeTestJson =
       ("""
          |{
          |  "modelType": "VwJNI",
          |  "modelId": { "id": 0, "name": "" },
          |  "numMissingThreshold": 0,
          |  "features": {
          |    "weight": "ind(${weight} / 10)"
          |  },
          |  "namespaces": {
          |    "personal_features": [ "height_mm", "weight", "hair" ]
          |  },
          |  "vw": {
          |    "params": [
          |      "--quiet",
          |      "-t",
          |      "-i """.stripMargin + VwModelPath + """"
          |    ]
          |  }
          |}
        """.stripMargin).trim.parseJson

    val noThreshJson =
        """
          |{
          |  "modelType": "VwJNI",
          |  "modelId": { "id": 0, "name": "" },
          |  "features": {
          |    "height": "ind(${height_cm} * 10)"
          |  },
          |  "namespaces": {
          |    "personal_features": [ "height" ]
          |  },
          |  "vw": {
          |    "params": [
          |      "--quiet",
          |      "-t"
          |    ]
          |  }
          |}
        """.stripMargin.trim.parseJson

    val threshJson =
        """
          |{
          |  "modelType": "VwJNI",
          |  "modelId": { "id": 0, "name": "" },
          |  "numMissingThreshold": 0,
          |  "features": {
          |    "height": "ind(${height_cm} * 10)"
          |  },
          |  "namespaces": {
          |    "personal_features": [ "height" ]
          |  },
          |  "vw": {
          |    "params": [
          |      "--quiet",
          |      "-t"
          |    ]
          |  }
          |}
        """.stripMargin.trim.parseJson

    val nsWithUndeclFeatureJson =
        """
          |{
          |  "modelType": "VwJNI",
          |  "modelId": { "id": 0, "name": "" },
          |  "numMissingThreshold": 0,
          |  "features": {
          |    "height": "Seq((\"\", 1.0))"
          |  },
          |  "namespaces": {
          |    "personal_features": [ "weight" ]
          |  },
          |  "vw": {
          |    "params": [
          |      "--quiet",
          |      "-t"
          |    ]
          |  }
          |}
        """.stripMargin.trim.parseJson

    val nonCoveringNsJson =
        """
          |{
          |  "modelType": "VwJNI",
          |  "modelId": { "id": 0, "name": "" },
          |  "numMissingThreshold": 0,
          |  "features": {
          |    "height": "Seq((\"\", 180.0))"
          |  },
          |  "vw": {
          |    "params": [
          |      "--quiet",
          |      "-t"
          |    ]
          |  }
          |}
        """.stripMargin.trim.parseJson


    val badVwArgsJson =
        """
          |{
          |  "modelType": "VwJNI",
          |  "modelId": { "id": 0, "name": "" },
          |  "features": { "height": "Seq((\"\", 180.0))" },
          |  "vw": {
          |    "params": "--quiet --BAD_FEATURE___ounq24tjnasdf8h"
          |  }
          |}
        """.stripMargin.trim.parseJson


    val csvLines = CsvLines(indices = columns.unzip._1.zipWithIndex.toMap, fs = ",")
    val plugin = CompiledSemanticsCsvPlugin(columns:_*)
    val semantics = CompiledSemantics(TwitterEvalCompiler(), plugin, Seq("scala.math._", "com.eharmony.matching.aloha.feature.BasicFunctions._"))

    val missingHeight = csvLines(",0,red").head


    /**
     * The output of the model created by createModel.
     */
    val ExpVwOutput = 0.50419676f

    /**
     * Don't call this manually.  Should only be called by the testing framework.  Is Idempotent though.
     */
    @BeforeClass def createModel(): Unit = {
        val x = for {
            deleted <- Try { VwModelFile.delete }
            _ <- allocateModel()
        } yield Unit

        if (x.isFailure) error(s"Couldn't properly allocate vw model: $VwModelPath")
    }

    private[this] def allocateModel(): Try[Unit] = {
        val m = new VWScorer(s"--quiet --loss_function logistic --link logistic -f $VwModelPath")
        1 to 100 foreach { _ =>
            m.doLearnAndGetPrediction("-1 | ")
            m.doLearnAndGetPrediction( "1 | ")
        }
        Try(())
    }
}
