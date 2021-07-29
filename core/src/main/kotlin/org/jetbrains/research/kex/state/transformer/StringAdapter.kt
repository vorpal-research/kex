package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateBuilder
import org.jetbrains.research.kex.state.predicate.predicate
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kthelper.collection.buildList
import org.jetbrains.research.kthelper.collection.dequeOf

class StringAdapter(val ctx: ExecutionContext) : RecollectingTransformer<StringAdapter> {
    override val builders = dequeOf(StateBuilder())
    val types get() = ctx.types

    private val TypeFactory.charSeqType get() = ctx.cm["java/lang/CharSequence"].type
    private val Type.array get() = types.getArrayType(this)

    private fun Class.getCtor(vararg argTypes: Type) =
        getMethod("<init>", types.voidType, *argTypes)

    private val Class.emptyInit
        get() =
            getCtor()
    private val Class.copyInit
        get() =
            getCtor(types.stringType)
    private val Class.charArrayInit
        get() =
            getCtor(types.charType.array)
    private val Class.charArrayWOffsetInit
        get() =
            getCtor(types.charType.array, types.intType, types.intType)

    private val Class.length
        get() =
            getMethod("length", types.intType)
    private val Class.isEmpty
        get() =
            getMethod("isEmpty", types.boolType)
    private val Class.charAt
        get() =
            getMethod("charAt", types.charType, types.intType)
    private val Class.equals
        get() =
            getMethod("equals", types.boolType, types.objectType)
    private val Class.startsWith
        get() =
            getMethod("startsWith", types.boolType, types.stringType)
    private val Class.startsWithOffset
        get() =
            getMethod("startsWith", types.boolType, types.stringType, types.intType)
    private val Class.endsWith
        get() =
            getMethod("endsWith", types.boolType, types.stringType)
    private val Class.indexOf
        get() =
            getMethod("indexOf", types.intType, types.intType)
    private val Class.indexOfWOffset
        get() =
            getMethod("indexOf", types.intType, types.intType, types.intType)
    private val Class.stringIndexOf
        get() =
            getMethod("indexOf", types.intType, types.stringType)
    private val Class.stringIndexOfWOffset
        get() =
            getMethod("indexOf", types.intType, types.stringType, types.intType)
    private val Class.substring
        get() =
            getMethod("substring", types.stringType, types.intType)
    private val Class.substringWLength
        get() =
            getMethod("substring", types.stringType, types.intType, types.intType)
    private val Class.subSequence
        get() =
            getMethod("subSequence", types.charSeqType, types.intType, types.intType)
    private val Class.concat
        get() =
            getMethod("concat", types.stringType, types.stringType)
    private val Class.contains
        get() =
            getMethod("contains", types.boolType, types.charSeqType)
    private val Class.toString
        get() =
            getMethod("toString", types.stringType)
    private val Class.compareTo
        get() =
            getMethod("compareTo", types.intType, types.stringType)

    private fun remap(predicate: Predicate, body: PredicateBuilder.() -> Predicate) =
        predicate(predicate.type, predicate.location, body)

    private fun <T : Any> T.list() = listOf(this)

    private fun generateCharArrayInit(
        predicate: Predicate,
        `this`: Term,
        charArray: Term,
        offset: Term = term { const(0) }
    ) = buildList<Predicate> {
        +remap(predicate) {
            forEach(offset, charArray.length()) {
                val lambdaParam = generate(KexInt())
                lambda(KexVoid(), listOf(lambdaParam)) {
                    state {
                        `this`.charAt(lambdaParam) equality charArray[lambdaParam].load()
                    }
                    apply()
                }
            }
        }
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val args = call.arguments

        val kfgString = ctx.cm.stringClass
        if (call.owner.type != kfgString.kexType) return predicate

        val `this` = call.owner
        val calledMethod = call.method

        val newPredicates = when (calledMethod) {
            kfgString.emptyInit -> return nothing()
            kfgString.copyInit -> remap(predicate) {
                `this` equality args[0]
            }.list()
            kfgString.charArrayInit -> generateCharArrayInit(predicate, `this`, args[0])
            kfgString.charArrayWOffsetInit -> generateCharArrayInit(predicate, `this`, args[0], args[1])
            kfgString.length -> remap(predicate) {
                predicate.lhv equality `this`.length()
            }.list()
            kfgString.isEmpty -> buildList {
                val lengthTerm = term { generate(KexInt()) }
                +remap(predicate) {
                    lengthTerm equality `this`.length()
                }
                +remap(predicate) {
                    predicate.lhv equality (lengthTerm ge 0)
                }
            }
            kfgString.charAt -> remap(predicate) {
                predicate.lhv equality `this`.charAt(args[0])
            }.list()
            kfgString.equals -> remap(predicate) {
                predicate.lhv equality (`this` equls args[0])
            }.list()
            kfgString.startsWith -> remap(predicate) {
                predicate.lhv equality `this`.startsWith(args[0])
            }.list()
            kfgString.startsWithOffset -> buildList {
                val offset = args[1]
                val wOffset = term { generate(KexString()) }
                val offsetLength = term { generate(KexInt()) }
                +remap(predicate) {
                    offsetLength equality (`this`.length() - offset)
                }
                +remap(predicate) {
                    wOffset equality `this`.substring(args[1], offsetLength)
                }
                +remap(predicate) {
                    predicate.lhv equality wOffset.startsWith(args[0])
                }
            }
            kfgString.endsWith -> remap(predicate) {
                predicate.lhv equality `this`.endsWith(args[0])
            }.list()
            kfgString.indexOf -> buildList {
                val substring = term { generate(KexString()) }
                +remap(predicate) {
                    substring equality args[0].toStr()
                }
                +remap(predicate) {
                    predicate.lhv equality `this`.indexOf(substring)
                }
            }
            kfgString.indexOfWOffset -> buildList {
                val substring = term { generate(KexString()) }
                +remap(predicate) {
                    substring equality args[0].toStr()
                }
                +remap(predicate) {
                    predicate.lhv equality `this`.indexOf(substring, args[1])
                }
            }
            kfgString.stringIndexOf -> remap(predicate) {
                predicate.lhv equality `this`.indexOf(args[0])
            }.list()
            kfgString.stringIndexOfWOffset -> remap(predicate) {
                predicate.lhv equality `this`.indexOf(args[0], args[1])
            }.list()
            kfgString.substring -> buildList {
                val substringLength = term { generate(KexInt()) }
                +remap(predicate) {
                    substringLength equality (`this`.length() - args[0])
                }
                +remap(predicate) {
                    predicate.lhv equality `this`.substring(args[0], substringLength)
                }
            }
            kfgString.substringWLength -> remap(predicate) {
                predicate.lhv equality `this`.substring(args[0], args[1])
            }.list()
            kfgString.subSequence -> remap(predicate) {
                predicate.lhv equality `this`.substring(args[0], args[1])
            }.list()
            kfgString.concat -> remap(predicate) {
                predicate.lhv equality (`this` `++` args[0])
            }.list()
            kfgString.contains -> remap(predicate) {
                predicate.lhv equality (args[0] `in` `this`)
            }.list()
            kfgString.toString -> remap(predicate) {
                predicate.lhv equality `this`
            }.list()
            kfgString.compareTo -> remap(predicate) {
                predicate.lhv equality `this`.cmp(args[0])
            }.list()
            else -> predicate.list()
        }
        for (statement in newPredicates) {
            currentBuilder += statement
        }
        return nothing()
    }

}