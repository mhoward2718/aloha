package com.eharmony.matching.featureSpecExtractor.csv

import com.eharmony.matching.aloha.semantics.compiled.CompiledSemantics
import com.eharmony.matching.aloha.semantics.func.GenAggFunc
import com.eharmony.matching.featureSpecExtractor.csv.json.{CsvColumnSpec, CsvJson1}
import com.eharmony.matching.featureSpecExtractor.{CompilerFailureMessages, FeatureExtractorFunction, SpecProducer, StringFeatureExtractorFunction}
import spray.json.JsValue

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

case class CsvSpecProducer1[A](nullString: String, separator: String)
    extends SpecProducer[A, CsvSpec1[A]]
    with CompilerFailureMessages {

    def this() = this(CsvSpecProducer1.NullString, CsvSpecProducer1.Separator)

    type JsonType = CsvJson1
    def name = getClass.getSimpleName
    def parse(json: JsValue): Try[CsvJson1] = Try { json.convertTo[CsvJson1] }
    def getSpec(semantics: CompiledSemantics[A], jsonSpec: CsvJson1): Try[CsvSpec1[A]] = {
        val spec = getCovariates(semantics, jsonSpec, nullString) map { cov => CsvSpec1(cov, separator) }
        spec
    }

    protected[this] def getCovariates(semantics: CompiledSemantics[A], cj: CsvJson1, nullString: String): Try[FeatureExtractorFunction[A, String]] = {
        // Get a new semantics with the imports changed to reflect the imports from the Json Spec
        // Import of ExecutionContext.Implicits.global is necessary.
        val semanticsWithImports = semantics.copy[A](imports = cj.imports)

        def compile(it: Iterator[CsvColumnSpec], successes: List[(String, GenAggFunc[A, String])]): Try[FeatureExtractorFunction[A, String]] = {
            if (!it.hasNext)
                Success { StringFeatureExtractorFunction(successes.reverse.toIndexedSeq) }
            else {
                val spec = it.next()

                val f = semanticsWithImports.createFunction[Option[spec.ColType]](spec.wrappedSpec, Some(spec.defVal))(spec.refInfo)
                f match {
                    case Left(msgs) => Failure { failure(spec.name, msgs) }
                    case Right(success) =>
                        val strFunc = success.andThenGenAggFunc(spec.finalizer(nullString))
                        compile(it, (spec.name, strFunc) :: successes)
                }
            }
        }

        compile(cj.features.iterator, Nil)
    }
}

object CsvSpecProducer1 {
    private val NullString = ""
    private val Separator = ","
}