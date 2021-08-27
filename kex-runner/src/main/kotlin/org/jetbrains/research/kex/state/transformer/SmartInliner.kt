//package org.jetbrains.research.kex.state.transformer
//
//import org.jetbrains.research.kex.asm.manager.MethodManager
//import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
//import org.jetbrains.research.kex.config.kexConfig
//import org.jetbrains.research.kex.ktype.KexClass
//import org.jetbrains.research.kex.ktype.KexRtManager.isKexRt
//import org.jetbrains.research.kex.ktype.kexType
//import org.jetbrains.research.kex.state.PredicateState
//import org.jetbrains.research.kex.state.StateBuilder
//import org.jetbrains.research.kex.state.predicate.CallPredicate
//import org.jetbrains.research.kex.state.predicate.Predicate
//import org.jetbrains.research.kex.state.predicate.state
//import org.jetbrains.research.kex.state.term.CallTerm
//import org.jetbrains.research.kex.state.term.Term
//import org.jetbrains.research.kfg.ir.ConcreteClass
//import org.jetbrains.research.kfg.ir.Method
//import org.jetbrains.research.kfg.type.TypeFactory
//import org.jetbrains.research.kthelper.collection.dequeOf
//import org.jetbrains.research.kthelper.tryOrNull
//
//private val defaultDepth = kexConfig.getIntValue("inliner", "depth", 5)
//
//class SmartInliner(
//    val types: TypeFactory,
//    val typeInfoMap: TypeInfoMap,
//    override val psa: PredicateStateAnalysis,
//    override val inlineSuffix: String = "inlined",
//    override var inlineIndex: Int = 0,
//    val maxDepth: Int = defaultDepth
//) : Inliner<SmartInliner> {
//
//    constructor(
//        types: TypeFactory,
//        psa: PredicateStateAnalysis,
//        inlineSuffix: String = "inlined",
//        inlineIndex: Int = 0,
//        maxDepth: Int = defaultDepth
//    ) : this(types, TypeInfoMap(), psa, inlineSuffix, inlineIndex, maxDepth)
//
//    override val builders = dequeOf(StateBuilder())
//    override val im = MethodManager.InlineManager
//    override var hasInlined: Boolean = false
//    var depth: Int = 1
//        private set
//
//    override fun isInlinable(method: Method): Boolean {
//        if (!im.inliningEnabled) return false
//        if (im.isIgnored(method)) return false
//        if (method.isKexRt) return true
//        return super.isInlinable(method)
//    }
//
//    override fun getInlinedMethod(callTerm: CallTerm): Method? {
//        val method = callTerm.method
//        return when {
//            method.isFinal -> method
//            method.isStatic -> method
//            method.isConstructor -> method
//            method.isKexRt -> method
//            else -> {
//                val typeInfo = typeInfoMap.getInfo<CastTypeInfo>(callTerm.owner) ?: return null
//                val kexClass = typeInfo.type as? KexClass ?: return null
//                val concreteClass = kexClass.kfgClass(types) as? ConcreteClass ?: return null
//                val result = tryOrNull { concreteClass.getMethod(method.name, method.desc) } ?: return null
//                when {
//                    result.isEmpty() -> null
//                    else -> result
//                }
//            }
//        }
//    }
//
//    override fun prepareInlinedState(method: Method, mappings: Map<Term, Term>): PredicateState? {
//        if (method.isEmpty()) return null
//
//        val builder = psa.builder(method)
//        val endState = builder.methodState ?: return null
//
//        return transform(endState) {
//            +TermRenamer("${inlineSuffix}${inlineIndex++}", mappings)
//            +KexRtAdapter(types.cm)
//        }
//    }
//
//    override fun apply(ps: PredicateState): PredicateState {
//        if (depth > maxDepth) return ps
//        return super.apply(ps)
//    }
//
//    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
//        val call = predicate.call as CallTerm
//        val calledMethod = call.method
//        if (!isInlinable(calledMethod)) return predicate
//
//        val inlinedMethod = getInlinedMethod(call) ?: return predicate
//        var (casts, mappings) = buildMappings(call, inlinedMethod, predicate.lhvUnsafe)
//
//        val callerClass = when (val kexType = call.owner.type) {
//            is KexClass ->  kexType.kfgClass(types)
//            else -> return predicate
//        }
//        var castPredicate: Predicate? = null
//        if (inlinedMethod.klass != callerClass) {
//            castPredicate = state {
//                val castType = inlinedMethod.klass.kexType
//                val casted = value(castType, "${call.owner}.casted${inlineIndex++}")
//                mappings = mappings.mapValues { if (it.value == call.owner) casted else it.value }
//                casted equality (call.owner `as` castType)
//            }
//        }
//        val inlinedState = prepareInlinedState(inlinedMethod, mappings) ?: return predicate
//        castPredicate?.run {
//            currentBuilder += this
//        }
//        casts.onEach { currentBuilder += it }
//        depth++
//        super.apply(inlinedState)
//        depth--
//        hasInlined = true
//        return nothing()
//    }
//}