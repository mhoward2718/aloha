package com.eharmony.matching.aloha.feature

import com.eharmony.matching.aloha.models.reg.RegressionModelValueToTupleConversions
import com.eharmony.matching.aloha.models.tree.decision.DecisionTreeBoolToOptBoolConversions

/**
  *
  */
object BasicFunctions
    extends DefaultPossessor
    with BasicMath
    with Comparisons
    with Sos2
    with CustomSos2
    with Indicator
    with Intercept
    with TimeConstants
    with RegressionModelValueToTupleConversions
    with DecisionTreeBoolToOptBoolConversions {

    final protected[this] val DefaultForMissingDataInReg = Seq(("=UNK", 1.0))
}
