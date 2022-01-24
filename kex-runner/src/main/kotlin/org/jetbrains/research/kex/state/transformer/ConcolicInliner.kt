package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.asArray
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.collection.dequeOf

class ConcolicInliner(val ctx: ExecutionContext,
                      override val psa: PredicateStateAnalysis,
                      override val inlineSuffix: String = "inlined",
                      override var inlineIndex: Int = 0
) : Inliner<ConcolicInliner> {
    private val knownTypes = hashMapOf<Term, KexType>()
    override val builders = dequeOf(StateBuilder())
    override var hasInlined: Boolean = false

    override fun apply(ps: PredicateState): PredicateState {
        return super.apply(ps)
    }

    override fun transformNewPredicate(predicate: NewPredicate): Predicate {
        knownTypes[predicate.lhv] = predicate.lhv.type
        return super.transformNewPredicate(predicate)
    }

    override fun transformNewArrayPredicate(predicate: NewArrayPredicate): Predicate {
        knownTypes[predicate.lhv] = predicate.dimensions.fold(predicate.elementType) { acc, _ -> acc.asArray() }
        return super.transformNewArrayPredicate(predicate)
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val calledMethod = call.method
        if (!isInlinable(calledMethod)) return predicate

        val inlinedMethod = getInlinedMethod(call) ?: return predicate
        var (casts, mappings) = buildMappings(call, inlinedMethod, predicate.lhvUnsafe)

        val callerClass = when (val kexType = call.owner.type) {
            is KexClass ->  kexType.kfgClass(ctx.types)
            else -> return predicate //unreachable { log.error("Unknown call owner $kexType") }
        }
        var castPredicate: Predicate? = null
        if (inlinedMethod.klass != callerClass) {
            castPredicate = state {
                val castType = inlinedMethod.klass.kexType
                val casted = value(castType, "${call.owner}.casted${inlineIndex++}")
                mappings = mappings.mapValues { if (it.value == call.owner) casted else it.value }
                casted equality (call.owner `as` castType)
            }
        }
        val inlinedState = prepareInlinedState(inlinedMethod, mappings) ?: return predicate
        castPredicate?.run {
            currentBuilder += this
        }
        casts.onEach { currentBuilder += it }
        currentBuilder += inlinedState
        hasInlined = true
        return nothing()
    }

    override fun transformArrayInitializerPredicate(predicate: ArrayInitializerPredicate): Predicate {
        return super.transformArrayInitializerPredicate(predicate)
    }

    override fun transformEnterMonitorPredicate(predicate: EnterMonitorPredicate): Predicate {
        return super.transformEnterMonitorPredicate(predicate)
    }

    override fun transformArrayStorePredicate(predicate: ArrayStorePredicate): Predicate {
        return super.transformArrayStorePredicate(predicate)
    }

    override fun transformInequalityPredicate(predicate: InequalityPredicate): Predicate {
        return super.transformInequalityPredicate(predicate)
    }

    override fun transformThrowPredicate(predicate: ThrowPredicate): Predicate {
        return super.transformThrowPredicate(predicate)
    }

    override fun transformBoundStorePredicate(predicate: BoundStorePredicate): Predicate {
        return super.transformBoundStorePredicate(predicate)
    }

    override fun transformFieldStorePredicate(predicate: FieldStorePredicate): Predicate {
        return super.transformFieldStorePredicate(predicate)
    }

    override fun transformCatchPredicate(predicate: CatchPredicate): Predicate {
        return super.transformCatchPredicate(predicate)
    }

    override fun transformDefaultSwitchPredicate(predicate: DefaultSwitchPredicate): Predicate {
        return super.transformDefaultSwitchPredicate(predicate)
    }

    override fun transformGenerateArrayPredicate(predicate: GenerateArrayPredicate): Predicate {
        return super.transformGenerateArrayPredicate(predicate)
    }

    override fun transformExitMonitorPredicate(predicate: ExitMonitorPredicate): Predicate {
        return super.transformExitMonitorPredicate(predicate)
    }

    override fun transformFieldInitializerPredicate(predicate: FieldInitializerPredicate): Predicate {
        return super.transformFieldInitializerPredicate(predicate)
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        when (val rhv = predicate.rhv) {
            is CastTerm -> {
                if (rhv.type.isSubtypeOf(ctx.types, rhv.operand.type)) {
                    knownTypes[rhv.operand] = rhv.type
                    knownTypes[predicate.lhv] = rhv.type
                } else {
                    knownTypes[predicate.lhv] = rhv.operand.type
                }
            }
            is ConstBoolTerm -> if (rhv.value) {
                when (val lhv = predicate.lhv) {
                    is InstanceOfTerm -> {
                        knownTypes[lhv.operand] = when (val known = knownTypes[lhv.operand]) {
                            null -> lhv.checkedType
                            else -> if (lhv.checkedType.isSubtypeOf(ctx.types, known)) lhv.checkedType
                            else known
                        }
                    }
                }
            }
            is ValueTerm, is ArgumentTerm, is ReturnValueTerm -> when (val lhv = predicate.lhv) {
                is ValueTerm, is ArgumentTerm, is ReturnValueTerm -> {
                    val lhvType = knownTypes[lhv]
                    val rhvType = knownTypes[rhv]
                    when {
                        lhvType != null && rhvType != null -> {
                            if (lhvType.isSubtypeOf(ctx.types, rhvType)) lhvType else rhvType
                        }
                        lhvType != null -> lhvType
                        rhvType != null -> rhvType
                        else -> null
                    }?.let {
                        knownTypes[lhv] = it
                        knownTypes[rhv] = it
                    }
                }
            }
        }
        return super.transformEqualityPredicate(predicate)
    }

    override fun transformConstByteTerm(term: ConstByteTerm): Term {
        return super.transformConstByteTerm(term)
    }

    override fun transformFieldLoadTerm(term: FieldLoadTerm): Term {
        return super.transformFieldLoadTerm(term)
    }

    override fun transformArgumentTerm(term: ArgumentTerm): Term {
        return super.transformArgumentTerm(term)
    }

    override fun transformForAllTerm(term: ForAllTerm): Term {
        return super.transformForAllTerm(term)
    }

    override fun transformLambdaTerm(term: LambdaTerm): Term {
        return super.transformLambdaTerm(term)
    }

    override fun transformExistsTerm(term: ExistsTerm): Term {
        return super.transformExistsTerm(term)
    }

    override fun transformEqualsTerm(term: EqualsTerm): Term {
        return super.transformEqualsTerm(term)
    }

    override fun transformArrayContainsTerm(term: ArrayContainsTerm): Term {
        return super.transformArrayContainsTerm(term)
    }

    override fun transformReturnValueTerm(term: ReturnValueTerm): Term {
        return super.transformReturnValueTerm(term)
    }

    override fun transformConstFloatTerm(term: ConstFloatTerm): Term {
        return super.transformConstFloatTerm(term)
    }

    override fun transformConstStringTerm(term: ConstStringTerm): Term {
        return super.transformConstStringTerm(term)
    }

    override fun transformConstShortTerm(term: ConstShortTerm): Term {
        return super.transformConstShortTerm(term)
    }

    override fun transformArrayLoadTerm(term: ArrayLoadTerm): Term {
        return super.transformArrayLoadTerm(term)
    }

    override fun transformConstDoubleTerm(term: ConstDoubleTerm): Term {
        return super.transformConstDoubleTerm(term)
    }

    override fun transformArrayLengthTerm(term: ArrayLengthTerm): Term {
        return super.transformArrayLengthTerm(term)
    }

    override fun transformCmpTerm(term: CmpTerm): Term {
        return super.transformCmpTerm(term)
    }

    override fun transformConstClassTerm(term: ConstClassTerm): Term {
        return super.transformConstClassTerm(term)
    }

    override fun transformFieldTerm(term: FieldTerm): Term {
        return super.transformFieldTerm(term)
    }

    override fun transformConstIntTerm(term: ConstIntTerm): Term {
        return super.transformConstIntTerm(term)
    }

    override fun transformStaticClassRefTerm(term: StaticClassRefTerm): Term {
        return super.transformStaticClassRefTerm(term)
    }

    override fun transformStringContainsTerm(term: StringContainsTerm): Term {
        return super.transformStringContainsTerm(term)
    }

    override fun transformNegTerm(term: NegTerm): Term {
        return super.transformNegTerm(term)
    }

    override fun transformValueTerm(term: ValueTerm): Term {
        return super.transformValueTerm(term)
    }

    override fun transformNullTerm(term: NullTerm): Term {
        return super.transformNullTerm(term)
    }

    override fun transformConstCharTerm(term: ConstCharTerm): Term {
        return super.transformConstCharTerm(term)
    }

    override fun transformIteTerm(term: IteTerm): Term {
        return super.transformIteTerm(term)
    }

    override fun transformArrayIndexTerm(term: ArrayIndexTerm): Term {
        return super.transformArrayIndexTerm(term)
    }

    override fun transformConstBoolTerm(term: ConstBoolTerm): Term {
        return super.transformConstBoolTerm(term)
    }

    override fun transformUndefTerm(term: UndefTerm): Term {
        return super.transformUndefTerm(term)
    }

    override fun transformCastTerm(term: CastTerm): Term {
        return super.transformCastTerm(term)
    }

    override fun transformCallTerm(term: CallTerm): Term {
        return super.transformCallTerm(term)
    }

    override fun transformBinaryTerm(term: BinaryTerm): Term {
        return super.transformBinaryTerm(term)
    }

    override fun transformConstLongTerm(term: ConstLongTerm): Term {
        return super.transformConstLongTerm(term)
    }

    override fun transformInstanceOfTerm(term: InstanceOfTerm): Term {
        return super.transformInstanceOfTerm(term)
    }

    override fun isInlinable(method: Method): Boolean = im.inliningEnabled && !im.isIgnored(method)

    override fun getInlinedMethod(callTerm: CallTerm): Method? {
        val method = callTerm.method
        return when {
            method.isFinal -> method
            method.isStatic -> method
            method.isConstructor -> method
            else -> {
                val kexClass = knownTypes[callTerm.owner] as? KexClass ?: return null
                val concreteClass = kexClass.kfgClass(ctx.types) as? ConcreteClass ?: return null
                val result = try {
                    concreteClass.getMethod(method.name, method.desc)
                } catch (e: Exception) {
                    return null
                }
                when {
                    result.isEmpty() -> null
                    else -> result
                }
            }
        }
    }

}