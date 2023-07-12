@file:Suppress("DuplicatedCode")

package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.KexBool
import org.vorpal.research.kex.ktype.KexChar
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexString
import org.vorpal.research.kex.ktype.asArray
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.PredicateQuery
import org.vorpal.research.kex.state.PredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.basic
import org.vorpal.research.kex.state.choice
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.PredicateBuilder
import org.vorpal.research.kex.state.predicate.assume
import org.vorpal.research.kex.state.predicate.predicate
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kex.state.term.LambdaTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.wrap
import org.vorpal.research.kex.util.StringInfoContext
import org.vorpal.research.kex.util.isSubtypeOfCached
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.charSequence
import org.vorpal.research.kfg.stringClass
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kthelper.collection.dequeOf

@Suppress("DEPRECATION")
@Deprecated("use StringMethodAdapter instead")
class StringAdapter(val cm: ClassManager) : StringInfoContext(), RecollectingTransformer<StringAdapter> {
    override val builders = dequeOf(StateBuilder())
    val types get() = cm.type

    private fun remap(predicate: Predicate, body: PredicateBuilder.() -> Predicate) =
        predicate(predicate.type, predicate.location, body)

    fun <T : Any> T.list() = listOf(this)

    private fun generateCharArrayInit(
        predicate: Predicate,
        `this`: Term,
        charArray: Term,
        offset: Term = term { const(0) }
    ) = buildList {
        val res = term { generate(KexBool) }
        this += remap(predicate) {
            res equality forAll(offset, charArray.length()) {
                val lambdaParam = generate(KexInt)
                lambda(KexBool, listOf(lambdaParam)) {
                    `this`.charAt(lambdaParam) eq charArray[lambdaParam].load()
                }
            }
        }

        this += assume {
            res equality true
        }
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val args = call.arguments

        val kfgString = cm.stringClass
        if (call.owner.type != kfgString.kexType) return predicate

        val `this` = call.owner

        val newPredicates = when (call.method) {
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
                val lengthTerm = term { generate(KexInt) }
                this += remap(predicate) {
                    lengthTerm equality `this`.length()
                }
                this += remap(predicate) {
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
                val offsetLength = term { generate(KexInt) }
                this += remap(predicate) {
                    offsetLength equality (`this`.length() - offset)
                }
                this += remap(predicate) {
                    wOffset equality `this`.substring(args[1], offsetLength)
                }
                this += remap(predicate) {
                    predicate.lhv equality wOffset.startsWith(args[0])
                }
            }

            kfgString.endsWith -> remap(predicate) {
                predicate.lhv equality `this`.endsWith(args[0])
            }.list()

            kfgString.indexOf -> buildList {
                val substring = term { generate(KexString()) }
                this += remap(predicate) {
                    substring equality args[0].toStr()
                }
                this += remap(predicate) {
                    predicate.lhv equality `this`.indexOf(substring)
                }
            }

            kfgString.indexOfWOffset -> buildList {
                val substring = term { generate(KexString()) }
                this += remap(predicate) {
                    substring equality args[0].toStr()
                }
                this += remap(predicate) {
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
                val substringLength = term { generate(KexInt) }
                this += remap(predicate) {
                    substringLength equality (`this`.length() - args[0])
                }
                this += remap(predicate) {
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

class StringMethodAdapter(
    val cm: ClassManager
) : StringInfoContext(), RecollectingTransformer<StringMethodAdapter>, IncrementalTransformer {
    override val builders = dequeOf(StateBuilder())
    val types get() = cm.type

    private fun Term.valueArray(): Term = term { this@valueArray.field(valueArrayType, valueArrayName) }

    private fun emptyInit(term: Term): PredicateState = basic {
        val emptyArray = generate(valueArrayType)
        state {
            emptyArray.new(0)
        }
        state {
            term.valueArray().store(emptyArray)
        }
    }

    private fun copyInit(term: Term, arg: Term): PredicateState = basic {
        val argArray = generate(valueArrayType)
        state {
            argArray equality arg.valueArray().load()
        }
        assume {
            (argArray neq null) equality true
        }
        state {
            term.valueArray().store(argArray)
        }
    }

    private fun charArrayInit(term: Term, arg: Term): PredicateState = basic {
        val valueArray = generate(valueArrayType)
        when (valueArray.type) {
            valueArrayType -> state {
                term.valueArray().store(arg)
            }
            else -> {
                state {
                    generateArray(valueArray, arg.length()) {
                        val index = value(KexInt, "lambda.index")
                        lambda(cm.type.objectType.kexType, index) {
                            arg[index].load() `as` valueArrayType.element
                        }
                    }
                }
                state {
                    term.valueArray().store(valueArray)
                }
            }
        }
    }

    private fun charArrayWOffsetInit(term: Term, array: Term, offset: Term, length: Term): PredicateState = basic {
        val valueArray = generate(valueArrayType)
        state {
            generateArray(valueArray, length) {
                val index = value(KexInt, "lambda.index")
                lambda(cm.type.objectType.kexType, index) {
                    array[offset + index].load() `as` valueArrayType.element
                }
            }
        }
        state {
            term.valueArray().store(valueArray)
        }
    }

    private fun length(lhv: Term, term: Term): PredicateState = basic {
        val fieldTerm = generate(valueArrayType)
        state {
            fieldTerm equality term.valueArray().load()
        }
        assume {
            (fieldTerm neq null) equality true
        }
        state {
            lhv equality fieldTerm.length()
        }
    }

    private fun isEmpty(lhv: Term, term: Term): PredicateState = basic {
        val fieldTerm = generate(valueArrayType)
        val length = generate(KexInt)
        state {
            fieldTerm equality term.valueArray().load()
        }
        assume {
            (fieldTerm neq null) equality true
        }
        state {
            length equality fieldTerm.length()
        }
        state {
            lhv equality (length eq 0)
        }
    }

    private fun charAt(lhv: Term, term: Term, index: Term): PredicateState = basic {
        val fieldTerm = generate(valueArrayType)
        val length = generate(KexInt)
        state {
            fieldTerm equality term.valueArray().load()
        }
        assume {
            (fieldTerm neq null) equality true
        }
        state {
            length equality fieldTerm.length()
        }
        assume {
            (index ge 0) equality true
        }
        assume {
            (index lt length) equality true
        }
        state {
            lhv equality (fieldTerm[index].load() `as` valueArrayType.element)
        }
    }

    private fun equals(lhv: Term, term: Term, other: Term): PredicateState {
        val isNull = term { generate(KexBool) }
        val instanceOf = term { generate(KexBool) }
        val res = term { generate(KexBool) }
        return basic {
            state { isNull equality (other eq null) }
            state { instanceOf equality (other `is` KexString()) }
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
            val thisValue = generate(valueArrayType)
            val otherValue = generate(valueArrayType)
            val thisLength = generate(KexInt)
            val otherLength = generate(KexInt)
            val lengthEquals = generate(KexBool)
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
                    assume {
                        (thisValue neq null) equality true
                    }
                    state {
                        otherValue equality casted.valueArray().load()
                    }
                    assume {
                        (otherValue neq null) equality true
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
                                    val index = generate(KexInt)
                                    lambda(cm.type.objectType.kexType, listOf(index)) {
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

    private fun startsWith(lhv: Term, term: Term, other: Term) = startsWithOffset(lhv, term, other, term { const(0) })

    private fun startsWithOffset(lhv: Term, term: Term, other: Term, offset: Term): PredicateState {
        val isGreater = term { generate(KexBool) }
        val res = term { generate(KexBool) }
        return basic {
            state { isGreater equality (offset ge 0) }
        }.choice {
            or {
                val thisValue = generate(valueArrayType)
                val otherValue = generate(valueArrayType)
                val thisLength = generate(KexInt)
                val otherLength = generate(KexInt)
                val lengthLess = generate(KexBool)
                basic {
                    path {
                        isGreater equality true
                    }
                    state {
                        thisValue equality term.valueArray().load()
                    }
                    assume {
                        (thisValue neq null) equality true
                    }
                    state {
                        otherValue equality other.valueArray().load()
                    }
                    assume {
                        (otherValue neq null) equality true
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
                                    val index = generate(KexInt)
                                    lambda(
                                        cm.type.objectType.kexType,
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

    private fun endsWith(lhv: Term, term: Term, other: Term): PredicateState {
        val isGreater = term { generate(KexBool) }
        val res = term { generate(KexBool) }
        val offset = term { generate(KexInt) }
        val thisValue = term { generate(valueArrayType) }
        val otherValue = term { generate(valueArrayType) }
        val thisLength = term { generate(KexInt) }
        val otherLength = term { generate(KexInt) }
        return basic {
            state {
                thisValue equality term.valueArray().load()
            }
            assume {
                (thisValue neq null) equality true
            }
            state {
                otherValue equality other.valueArray().load()
            }
            assume {
                (otherValue neq null) equality true
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
                            val index = generate(KexInt)
                            lambda(
                                cm.type.objectType.kexType,
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

    private fun substring(lhv: Term, term: Term, beginIndex: Term) =
        substringWLength(lhv, term, beginIndex, term { term.valueArray().load().length() })

    private fun substringWLength(lhv: Term, term: Term, beginIndex: Term, endIndex: Term): PredicateState {
        val isGreater = term { generate(KexBool) }
        val res = term { generate(KexString()) }
        val resValue = term { generate(valueArrayType) }
        val length = term { generate(KexInt) }
        val thisValue = term { generate(valueArrayType) }
        return basic {
            state {
                thisValue equality term.valueArray().load()
            }
            assume {
                (thisValue neq null) equality true
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
                            val index = generate(KexInt)
                            lambda(
                                cm.type.objectType.kexType,
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

    private fun subSequence(lhv: Term, term: Term, beginIndex: Term, endIndex: Term) =
        substringWLength(lhv, term, beginIndex, endIndex)

    private fun concat(lhv: Term, term: Term, other: Term) = basic {
        val thisValue = generate(valueArrayType)
        val otherValue = generate(valueArrayType)
        val thisLength = generate(KexInt)
        val otherLength = generate(KexInt)
        val resLength = generate(KexInt)
        val resValue = generate(valueArrayType)
        val res = generate(KexString())
        state {
            thisValue equality term.valueArray().load()
        }
        assume {
            (thisValue neq null) equality true
        }
        state {
            otherValue equality other.valueArray().load()
        }
        assume {
            (otherValue neq null) equality true
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
                val index = generate(KexInt)
                lambda(cm.type.objectType.kexType, index) {
                    ite(
                        KexChar,
                        index lt thisLength,
                        thisValue[index].load(),
                        otherValue[index - thisLength].load()
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

    private fun toString(lhv: Term, term: Term) = basic {
        state {
            lhv equality term
        }
    }

    private fun toCharArray(lhv: Term, term: Term) = basic {
        state {
            lhv equality term.valueArray().load()
        }
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val args = call.arguments
        if (call.owner.type !is KexClass) return predicate

        val kfgString = cm.stringClass
        val kfgCharSequence = cm.charSequence
        val kfgOwnerType = (call.owner.type.getKfgType(cm.type) as ClassType).klass
        if (!kfgOwnerType.asType.isSubtypeOfCached(kfgCharSequence.asType)) return predicate


        val `this` = when (kfgOwnerType) {
            kfgString -> call.owner
            kfgCharSequence -> {
                val stringTerm = term { generate(kfgString.kexType) }
                currentBuilder += assume {
                    stringTerm equality (call.owner `as` kfgString.kexType)
                }
                stringTerm
            }

            else -> return predicate
        }
        val calledMethod = call.method
        currentBuilder += when (calledMethod) {
            kfgString.emptyInit -> emptyInit(`this`)
            kfgString.copyInit -> copyInit(`this`, args[0])
            kfgString.charArrayInit -> charArrayInit(`this`, args[0])
            kfgString.charArrayWOffsetInit -> charArrayWOffsetInit(`this`, args[0], args[1], args[2])
            kfgString.length, kfgCharSequence.length -> length(predicate.lhv, `this`)
            kfgString.isEmpty -> isEmpty(predicate.lhv, `this`)
            kfgString.charAt, kfgCharSequence.charAt -> charAt(predicate.lhv, `this`, args[0])
            kfgString.equals, kfgCharSequence.charAt -> equals(predicate.lhv, `this`, args[0])
            kfgString.startsWith -> startsWith(predicate.lhv, `this`, args[0])
            kfgString.startsWithOffset -> startsWithOffset(predicate.lhv, `this`, args[0], args[1])
            kfgString.endsWith -> endsWith(predicate.lhv, `this`, args[0])
            kfgString.substring -> substring(predicate.lhv, `this`, args[0])
            kfgString.substringWLength -> substringWLength(predicate.lhv, `this`, args[0], args[1])
            kfgString.subSequence, kfgCharSequence.charAt -> subSequence(predicate.lhv, `this`, args[0], args[1])
            kfgString.concat -> concat(predicate.lhv, `this`, args[0])
            kfgString.toString, kfgCharSequence.charAt -> toString(predicate.lhv, `this`)
            kfgString.toCharArray, kfgCharSequence.charAt -> toCharArray(predicate.lhv, `this`)
            else -> predicate.wrap()
        }
        return nothing()
    }

    override fun transformLambdaTerm(term: LambdaTerm): Term {
        val newBody = TermExprStringAdapter(cm).transform(term.body)
        return term { lambda(term.type, term.parameters, newBody) }
    }

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState {
        return IncrementalPredicateState(
            apply(state.state),
            state.queries.map { query ->
                PredicateQuery(
                    apply(query.hardConstraints),
                    query.softConstraints
                )
            }
        )
    }

}

class TermExprStringAdapter(val cm: ClassManager) : StringInfoContext(), Transformer<TermExprStringAdapter> {
    private fun Term.valueArray(): Term = term { this@valueArray.field(kexCharArray(), "value") }
    private fun kexCharArray() = KexChar.asArray()

    override fun transformCallTerm(term: CallTerm): Term {
        val args = term.arguments

        val kfgString = cm.stringClass
        if (term.owner.type != kfgString.kexType) return term

        val `this` = term.owner
        return when (term.method) {
            kfgString.length -> term { `this`.valueArray().load().length() }
            kfgString.isEmpty -> term { `this`.valueArray().load().length() eq 0 }
            kfgString.charAt -> term { `this`.valueArray().load()[args[0]].load() }
            kfgString.equals -> term {
                val other = args[0]
                ite(
                    KexBool,
                    other eq null,
                    const(false),
                    ite(
                        KexBool,
                        other `is` KexString(),
                        forAll(0, `this`.valueArray().load().length()) {
                            val index = generate(KexInt)
                            lambda(cm.type.objectType.kexType, listOf(index)) {
                                `this`.valueArray().load()[index].load() eq (other `as` KexString()).valueArray()
                                    .load()[index].load()
                            }
                        },
                        const(false)
                    )
                )
            }
//            kfgString.startsWith -> startsWith(predicate.lhv, `this`, args[0])
//            kfgString.startsWithOffset -> startsWithOffset(predicate.lhv, `this`, args[0], args[1])
//            kfgString.endsWith -> endsWith(predicate.lhv, `this`, args[0])
//            kfgString.substring -> substring(predicate.lhv, `this`, args[0])
//            kfgString.substringWLength -> substringWLength(predicate.lhv, `this`, args[0], args[1])
//            kfgString.subSequence -> subSequence(predicate.lhv, `this`, args[0], args[1])
//            kfgString.concat -> concat(predicate.lhv, `this`, args[0])
            kfgString.toString -> `this`
            kfgString.toCharArray -> term { term.valueArray().load() }
            else -> term
        }
    }
}
