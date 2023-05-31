package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.KexRtManager.rtUnmapped
import org.vorpal.research.kex.state.predicate.PredicateBuilder
import org.vorpal.research.kex.state.predicate.PredicateType
import org.vorpal.research.kex.state.term.*
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.ir.Location
import ru.spbstu.wheels.mapToArray

fun FieldTerm.unmappedKfgField(cm: ClassManager): Field {
    val kfgKlass = cm[this.klass]
    return  when {
        kfgKlass.isKexRt -> kfgKlass.getField(fieldName, type.getKfgType(cm.type))
        else -> kfgKlass.getField(fieldName, type.rtUnmapped.getKfgType(cm.type))
    }
}

class KexRtAdapter(val cm: ClassManager) : PredicateBuilder, Transformer<KexRtAdapter> {
    override val type = PredicateType.State()
    override val location = Location()

    override fun transformArgumentTerm(term: ArgumentTerm): Term {
        return arg(term.type.rtMapped, term.index)
    }

    override fun transformArrayIndex(term: ArrayIndexTerm): Term {
        return term.arrayRef[term.index]
    }

    override fun transformCallTerm(term: CallTerm): Term {
        val method = term.method
        val mappedKlass = method.klass.rtMapped
        if (!mappedKlass.isKexRt) return term

        val mappedMethod = mappedKlass.getMethod(
            method.name,
            method.returnType.rtMapped,
            *method.argTypes.mapToArray { it.rtMapped }
        )
        return term.owner.call(mappedMethod, term.arguments)
    }

    override fun transformCastTerm(term: CastTerm): Term {
        val newCast = term.type.rtMapped
        return term.operand `as` newCast
    }

    override fun transformConstClass(term: ConstClassTerm): Term {
        return `class`(term.type, term.constantType.rtMapped)
    }

    override fun transformInstanceOfTerm(term: InstanceOfTerm): Term {
        val newCheck = term.checkedType.rtMapped
        return term.operand `is` newCheck
    }

    override fun transformFieldTerm(term: FieldTerm): Term {
        val unreferenced = (term.type as KexReference).reference
        return term.owner.field(unreferenced.rtMapped, term.fieldName)
    }

    override fun transformStaticClassRefTerm(term: StaticClassRefTerm): Term {
        return staticRef(term.type.rtMapped as KexClass)
    }

    override fun transformUndefTerm(term: UndefTerm): Term {
        return undef(term.type.rtMapped)
    }

    override fun transformValueTerm(term: ValueTerm): Term {
        return value(term.type.rtMapped, term.name)
    }
}
