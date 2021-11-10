package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.state.*
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kthelper.collection.buildList
import org.jetbrains.research.kthelper.collection.dequeOf


private val TypeFactory.charSeqType get() = cm["java/lang/CharSequence"].type
private fun Type.getArray(types: TypeFactory) = types.getArrayType(this)

fun Class.getCtor(vararg argTypes: Type) =
    getMethod("<init>", cm.type.voidType, *argTypes)

private val Class.emptyInit
    get() = getCtor()
private val Class.copyInit
    get() = getCtor(cm.type.stringType)
private val Class.charArrayInit
    get() = getCtor(cm.type.charType.getArray(cm.type))
private val Class.charArrayWOffsetInit
    get() = getCtor(cm.type.charType.getArray(cm.type), cm.type.intType, cm.type.intType)

private val Class.length
    get() = getMethod("length", cm.type.intType)
private val Class.isEmpty
    get() = getMethod("isEmpty", cm.type.boolType)
private val Class.charAt
    get() = getMethod("charAt", cm.type.charType, cm.type.intType)
private val Class.equals
    get() = getMethod("equals", cm.type.boolType, cm.type.objectType)
private val Class.startsWith
    get() = getMethod("startsWith", cm.type.boolType, cm.type.stringType)
private val Class.startsWithOffset
    get() = getMethod("startsWith", cm.type.boolType, cm.type.stringType, cm.type.intType)
private val Class.endsWith
    get() = getMethod("endsWith", cm.type.boolType, cm.type.stringType)
private val Class.indexOf
    get() = getMethod("indexOf", cm.type.intType, cm.type.intType)
private val Class.indexOfWOffset
    get() = getMethod("indexOf", cm.type.intType, cm.type.intType, cm.type.intType)
private val Class.stringIndexOf
    get() = getMethod("indexOf", cm.type.intType, cm.type.stringType)
private val Class.stringIndexOfWOffset
    get() = getMethod("indexOf", cm.type.intType, cm.type.stringType, cm.type.intType)
private val Class.substring
    get() = getMethod("substring", cm.type.stringType, cm.type.intType)
private val Class.substringWLength
    get() = getMethod("substring", cm.type.stringType, cm.type.intType, cm.type.intType)
private val Class.subSequence
    get() = getMethod("subSequence", cm.type.charSeqType, cm.type.intType, cm.type.intType)
private val Class.concat
    get() = getMethod("concat", cm.type.stringType, cm.type.stringType)
private val Class.contains
    get() = getMethod("contains", cm.type.boolType, cm.type.charSeqType)
private val Class.toString
    get() = getMethod("toString", cm.type.stringType)
private val Class.compareTo
    get() = getMethod("compareTo", cm.type.intType, cm.type.stringType)

@Suppress("DEPRECATION")
@Deprecated("use StringMethodAdapter instead")
class StringAdapter(val cm: ClassManager) : RecollectingTransformer<StringAdapter> {
    override val builders = dequeOf(StateBuilder())
    val types get() = cm.type

    fun remap(predicate: Predicate, body: PredicateBuilder.() -> Predicate) =
        predicate(predicate.type, predicate.location, body)

    fun <T : Any> T.list() = listOf(this)

    private fun generateCharArrayInit(
        predicate: Predicate,
        `this`: Term,
        charArray: Term,
        offset: Term = term { const(0) }
    ) = buildList<Predicate> {
        val res = term { generate(KexBool()) }
        +remap(predicate) {
            res equality forAll(offset, charArray.length()) {
                val lambdaParam = generate(KexInt())
                lambda(KexBool(), listOf(lambdaParam)) {
                    `this`.charAt(lambdaParam) eq charArray[lambdaParam].load()
                }
            }
        }
        +assume {
            res equality true
        }
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val args = call.arguments

        val kfgString = cm.stringClass
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

class StringMethodAdapter(val cm: ClassManager) : RecollectingTransformer<StringMethodAdapter> {
    override val builders = dequeOf(StateBuilder())
    val types get() = cm.type

    private fun Term.valueArray(): Term = term { this@valueArray.field(KexCharArray(), "value") }
    private fun KexCharArray() = KexChar().asArray()

    fun emptyInit(term: Term): PredicateState = basic {
        val emptyArray = generate(KexCharArray())
        state {
            emptyArray.new(0)
        }
        state {
            term.valueArray().store(emptyArray)
        }
    }

    fun copyInit(term: Term, arg: Term): PredicateState = basic {
        val argArray = generate(KexCharArray())
        state {
            argArray equality arg.valueArray().load()
        }
        state {
            term.valueArray().store(argArray)
        }
    }

    fun charArrayInit(term: Term, arg: Term): PredicateState = basic {
        state {
            term.valueArray().store(arg)
        }
    }

    fun charArrayWOffsetInit(term: Term, array: Term, offset: Term, length: Term): PredicateState = basic {
        val valueArray = generate(KexCharArray())
        state {
            generateArray(valueArray, length) {
                val index = value(KexInt(), "lambda.index")
                lambda(types.objectType.kexType, index) {
                    array[offset + index].load()
                }
            }
        }
        state {
            term.valueArray().store(valueArray)
        }
    }

    fun length(lhv: Term, term: Term): PredicateState = basic {
        val fieldTerm = generate(KexCharArray())
        state {
            fieldTerm equality term.valueArray().load()
        }
        state {
            lhv equality fieldTerm.length()
        }
    }

    fun isEmpty(lhv: Term, term: Term): PredicateState = basic {
        val fieldTerm = generate(KexCharArray())
        val length = generate(KexInt())
        state {
            fieldTerm equality term.valueArray().load()
        }
        state {
            length equality fieldTerm.length()
        }
        state {
            lhv equality (length eq 0)
        }
    }

    fun charAt(lhv: Term, term: Term, index: Term): PredicateState = basic {
        val fieldTerm = generate(KexCharArray())
        state {
            fieldTerm equality term.valueArray().load()
        }
        state {
            lhv equality fieldTerm[index].load()
        }
    }

    fun equals(lhv: Term, term: Term, other: Term): PredicateState {
        val isNull = term { generate(KexBool()) }
        val instanceOf = term { generate(KexBool()) }
        val res = term { generate(KexBool()) }
        return basic {
            state { isNull equality (other eq null) }
            state { instanceOf equality (other `is` KexString())}
        }.choice {
            or {
                basic {
                    path {
                        isNull equality true
                    }
                    state {
                        res equality false
                    }
                }
            }
            or {
                basic {
                    path {
                        isNull equality false
                    }
                    path {
                        instanceOf equality false
                    }
                    state {
                        res equality false
                    }
                }
            }

            val casted = generate(KexString())
            val thisValue = generate(KexCharArray())
            val otherValue = generate(KexCharArray())
            val thisLength = generate(KexInt())
            val otherLength = generate(KexInt())
            val lengthEquals = generate(KexBool())
            or {
                basic {
                    path {
                        isNull equality false
                    }
                    path {
                        instanceOf equality true
                    }

                    state {
                        casted equality (other `as` KexString())
                    }

                    state {
                        thisValue equality term.valueArray().load()
                    }
                    state {
                        otherValue equality casted.valueArray().load()
                    }
                    state {
                        thisLength equality thisValue.length()
                    }
                    state {
                        otherLength equality otherValue.length()
                    }
                    state {
                        lengthEquals equality (thisLength eq otherLength)
                    }
                }.choice {
                    or {
                        basic {
                            path {
                                lengthEquals equality true
                            }
                            state {
                                res equality forAll(0, thisLength) {
                                    val index = generate(KexInt())
                                    lambda(types.objectType.kexType, listOf(index)) {
                                        thisValue[index].load() eq otherValue[index].load()
                                    }
                                }
                            }
                        }
                    }
                    or {
                        basic {
                            path {
                                lengthEquals equality false
                            }
                            state {
                                res equality false
                            }
                        }
                    }
                }
            }
        }.basic {
            state {
                lhv equality res
            }
        }
    }

    fun startsWith(lhv: Term, term: Term, other: Term) = startsWithOffset(lhv, term, other, term { const(0) })

    fun startsWithOffset(lhv: Term, term: Term, other: Term, offset: Term): PredicateState {
        val isGreater = term { generate(KexBool()) }
        val res = term { generate(KexBool()) }
        return basic {
            state { isGreater equality (offset ge 0) }
        }.choice {
            or {
                val thisValue = generate(KexCharArray())
                val otherValue = generate(KexCharArray())
                val thisLength = generate(KexInt())
                val otherLength = generate(KexInt())
                val lengthLess = generate(KexBool())
                basic {
                    path {
                        isGreater equality true
                    }
                    state {
                        thisValue equality term.valueArray().load()
                    }
                    state {
                        otherValue equality other.valueArray().load()
                    }
                    state {
                        thisLength equality thisValue.length()
                    }
                    state {
                        otherLength equality otherValue.length()
                    }
                    state {
                        lengthLess equality ((thisLength - offset) lt otherLength)
                    }
                }.choice {
                    or {
                        basic {
                            path {
                                lengthLess equality true
                            }
                            state {
                                res equality forAll(0, otherLength) {
                                    val index = generate(KexInt())
                                    lambda(
                                        types.objectType.kexType,
                                        listOf(index)
                                    ) {
                                        thisValue[offset + index].load() eq otherValue[index].load()
                                    }
                                }
                            }
                        }
                    }
                    or {
                        basic {
                            path {
                                lengthLess equality false
                            }
                            state {
                                res equality false
                            }
                        }
                    }
                }
            }
            or {
                basic {
                    path {
                        isGreater equality false
                    }
                    state {
                        res equality false
                    }
                }
            }
        }.basic {
            state {
                lhv equality res
            }
        }
    }

    fun endsWith(lhv: Term, term: Term, other: Term): PredicateState {
        val isGreater = term { generate(KexBool()) }
        val res = term { generate(KexBool()) }
        val offset = term { generate(KexInt()) }
        val thisValue = term { generate(KexCharArray()) }
        val otherValue = term { generate(KexCharArray()) }
        val thisLength = term { generate(KexInt()) }
        val otherLength = term { generate(KexInt()) }
        return basic {
            state {
                thisValue equality term.valueArray().load()
            }
            state {
                otherValue equality other.valueArray().load()
            }
            state {
                thisLength equality thisValue.length()
            }
            state {
                otherLength equality otherValue.length()
            }
            state {
                offset equality (thisLength - otherLength)
            }
            state { isGreater equality (offset ge 0) }
        }.choice {
            or {
                basic {
                    path {
                        isGreater equality true
                    }
                    state {
                        res equality forAll(0, otherLength) {
                            val index = generate(KexInt())
                            lambda(
                                types.objectType.kexType,
                                listOf(index)
                            ) {
                                thisValue[offset + index].load() eq otherValue[index].load()
                            }
                        }
                    }
                }
            }
            or {
                basic {
                    path {
                        isGreater equality false
                    }
                    state {
                        res equality false
                    }
                }
            }
        }.basic {
            state {
                lhv equality res
            }
        }
    }

    fun substring(lhv: Term, term: Term, beginIndex: Term) = substringWLength(lhv, term, beginIndex, term { term.valueArray().load().length() })

    fun substringWLength(lhv: Term, term: Term, beginIndex: Term, endIndex: Term): PredicateState {
        val isGreater = term { generate(KexBool()) }
        val res = term { generate(KexString()) }
        val resValue = term { generate(KexCharArray()) }
        val length = term { generate(KexInt()) }
        val thisValue = term { generate(KexCharArray()) }
        return basic {
            state {
                thisValue equality term.valueArray().load()
            }
            state {
                length equality (endIndex - beginIndex)
            }
            state { isGreater equality (length ge 0) }
        }.choice {
            or {
                basic {
                    path {
                        isGreater equality true
                    }
                    state {
                        generateArray(resValue, length) {
                            val index = generate(KexInt())
                            lambda(
                                types.objectType.kexType,
                                listOf(index)
                            ) {
                                thisValue[beginIndex + index].load()
                            }
                        }
                    }
                    state {
                        res.new()
                    }
                    state {
                        res.valueArray().store(resValue)
                    }
                }
            }
            or {
                basic {
                    path {
                        isGreater equality false
                    }
                    state {
                        res equality null
                    }
                }
            }
        }.basic {
            state {
                lhv equality res
            }
        }
    }

    fun subSequence(lhv: Term, term: Term, beginIndex: Term, endIndex: Term) = substringWLength(lhv, term, beginIndex, endIndex)

    fun concat(lhv: Term, term: Term, other: Term) = basic {
        val thisValue = generate(KexCharArray())
        val otherValue = generate(KexCharArray())
        val thisLength = generate(KexInt())
        val otherLength = generate(KexInt())
        val resLength = generate(KexInt())
        val resValue = generate(KexCharArray())
        val res = generate(KexString())
        state {
            thisValue equality term.valueArray().load()
        }
        state {
            otherValue equality other.valueArray().load()
        }
        state {
            thisLength equality thisValue.length()
        }
        state {
            otherLength equality otherValue.length()
        }
        state {
            resLength equality (thisLength + otherLength)
        }
        state {
            generateArray(resValue, resLength) {
                val index = generate(KexInt())
                lambda(types.objectType.kexType, index) {
                    ite(KexChar(),
                        index lt thisLength,
                        thisValue[index].load(),
                        otherValue[index - thisValue].load()
                    )
                }
            }
        }
        state {
            res.new()
        }
        state {
            res.valueArray().store(resValue)
        }
        state {
            lhv equality res
        }
    }

    fun toString(lhv: Term, term: Term) = basic {
        state {
            lhv equality term
        }
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val args = call.arguments

        val kfgString = cm.stringClass
        if (call.owner.type != kfgString.kexType) return predicate

        val `this` = call.owner
        val calledMethod = call.method

        currentBuilder += when (calledMethod) {
            kfgString.emptyInit -> emptyInit(`this`)
            kfgString.copyInit -> copyInit(`this`, args[0])
            kfgString.charArrayInit -> charArrayInit(`this`, args[0])
            kfgString.charArrayWOffsetInit -> charArrayWOffsetInit(`this`, args[0], args[1], args[2])
            kfgString.length -> length(predicate.lhv, `this`)
            kfgString.isEmpty -> isEmpty(predicate.lhv, `this`)
            kfgString.charAt -> charAt(predicate.lhv, `this`, args[0])
            kfgString.equals -> equals(predicate.lhv, `this`, args[0])
            kfgString.startsWith -> startsWith(predicate.lhv, `this`, args[0])
            kfgString.startsWithOffset -> startsWithOffset(predicate.lhv, `this`, args[0], args[1])
            kfgString.endsWith -> endsWith(predicate.lhv, `this`, args[0])
            kfgString.substring -> substring(predicate.lhv, `this`, args[0])
            kfgString.substringWLength -> substringWLength(predicate.lhv, `this`, args[0], args[1])
            kfgString.subSequence -> subSequence(predicate.lhv, `this`, args[0], args[1])
            kfgString.concat -> concat(predicate.lhv, `this`, args[0])
            kfgString.toString -> toString(predicate.lhv, `this`)
            else -> predicate.wrap()
        }
        return nothing()
    }

}