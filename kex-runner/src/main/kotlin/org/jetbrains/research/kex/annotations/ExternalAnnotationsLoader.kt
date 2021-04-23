package org.jetbrains.research.kex.annotations

import org.jetbrains.research.kthelper.assert.ktassert
import org.w3c.dom.Element
import java.io.File
import java.io.InputStream
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class ExternalAnnotationsLoader : AnnotationsLoader {
    private val root = PackageTreeNode("", null)

    fun loadFrom(path: File) = scanSubTree(path)
    fun loadFrom(stream: InputStream) = loadAnnotations(stream, "[stream source]")
    fun loadFrom(url: URL) = loadAnnotations(url.openStream(), url.toString())

    private fun scanSubTree(path: File) {
        if (path.name == "annotations.xml") {
            loadAnnotations(path.inputStream(), path.absolutePath)
        }
        if (path.isDirectory) {
            for (file in path.listFiles()!!) {
                scanSubTree(file)
            }
        }
    }

    private fun findPackage(name: String, emplace: Boolean = false): PackageTreeNode? {
        var current = root
        val names = name.split('/')
        for (word in names) {
            when (val p = current.nodes.find { it.name == word }) {
                null -> when {
                    !emplace -> return null
                    else -> {
                        val newPackage = PackageTreeNode(word, current)
                        current.nodes += newPackage
                        current = newPackage
                    }
                }
                else -> current = p
            }
        }
        return current
    }

    private fun emplacePackage(name: String) = findPackage(name, true)!!

    private fun getCallOverloadsPrivate(name: String): Sequence<MutableAnnotatedCall> {
        val i = name.lastIndexOf('.')
        val packageName = name.substring(0 until i)
        val callName = name.substring(i + 1)
        val packageNode = findPackage(packageName) ?: return emptySequence()
        return packageNode.entities.asSequence().filter { it.name == callName }
    }

    override fun getCallOverloads(name: String): Sequence<AnnotatedCall> = getCallOverloadsPrivate(name)

    private fun emplaceCall(packageName: String,
                            callName: String,
                            returnType: String,
                            params: List<String>): MutableAnnotatedCall {
        val node = emplacePackage(packageName)
        val call = node.entities.find {
            when {
                it.name == callName && params.size == it.params.size ->
                    params.withIndex().all { (index, param) -> param == it.params[index].type }
                else -> false
            }
        }
        return when (call) {
            null -> {
                val type = if (node.name == callName) CallType.Constructor else CallType.Method
                val callParams = MutableList(params.size) {
                    MutableAnnotatedParam(params[it])
                }
                val newCall = MutableAnnotatedCall(node, callName, type, returnType, callParams)
                node.entities += newCall
                newCall
            }
            else -> call
        }
    }

    private fun getExactCallPrivate(name: String, vararg params: String): MutableAnnotatedCall? {
        return getCallOverloadsPrivate(name).find {
            when (params.size) {
                it.params.size -> params.withIndex().all { (index, param) -> param == it.params[index].type }
                else -> false
            }
        }
    }

    override fun getExactCall(name: String, vararg params: String): AnnotatedCall? = getExactCallPrivate(name, *params)

    private fun parseParamTypes(paramsStr: String) =
            when {
                paramsStr.isBlank() -> emptyList()
                else -> paramsStr.split(',').map { transformTypeName(it.trim()) }
            }

    private fun transformTypeName(name: String) = when (name) {
        "boolean" -> "bool"
        else -> name.replace('.', '/')
                .replace("///", "...")
                .replace(" ", "")
                .takeWhile { it != '<' }
    }

    private fun parseAnnotations(node: Element): List<AnnotationInfo> {
        val result = mutableListOf<AnnotationInfo>()
        val nodes = node.getElementsByTagName("annotation")
        var i = 0
        for (annotationNode in generateSequence { if (i == nodes.length) null else nodes.item(i++) as Element }) {
            val name = annotationNode.attributes.getNamedItem("name").nodeValue
            val paramNodes = annotationNode.getElementsByTagName("val")
            val args = mutableMapOf<String, String>()
            for (j in 0 until paramNodes.length) {
                val paramNode = paramNodes.item(j)
                val attributes = paramNode.attributes
                args[attributes.getNamedItem("name")?.nodeValue ?: "value"] =
                        attributes.getNamedItem("val")?.nodeValue ?: paramNode.textContent.trim()
            }
            result += AnnotationManager.build(name, args) ?: continue
        }
        return result
    }

    private fun loadAnnotations(stream: InputStream, file: String) {
        try {
            val xmlDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
            xmlDoc.documentElement.normalize()
            check(xmlDoc.documentElement.nodeName == "root") {
                "Annotations xml document root name is not \"root\"," +
                        "actual name is \"${xmlDoc.documentElement.nodeName}\""
            }
            val nodes = xmlDoc.getElementsByTagName("item")
            var i = 0
            for (node in generateSequence { if (i == nodes.length) null else nodes.item(i++) }) {
                val nameAttr = node.attributes.getNamedItem("name").nodeValue.trim()
                var delimiter = nameAttr.indexOf(' ')
                if (delimiter < 0)
                    // this is not a call
                    continue
                val packageName = nameAttr.substring(0 until delimiter).replace('.', '/')
                while (nameAttr[delimiter] == ' ') delimiter++
                var j = nameAttr.indexOfAny(charArrayOf(' ', '('), delimiter)
                var returnType = nameAttr.substring(delimiter until j)
                val callName: String
                if (nameAttr[j] == '(') {
                    callName = "<init>"
                    returnType = packageName
                } else {
                    delimiter = j + 1
                    while (nameAttr[delimiter] == ' ') delimiter++
                    j = nameAttr.indexOfAny(charArrayOf('('), delimiter)
                    callName = nameAttr.substring(delimiter until j)
                }
                returnType = transformTypeName(returnType)
                ktassert(nameAttr[j] == '(')
                delimiter = j + 1
                while (nameAttr[delimiter] == ' ') delimiter++
                j = nameAttr.indexOf(')', delimiter)
                ktassert(j > 0)
                val params = parseParamTypes(nameAttr.substring(delimiter until j))
                val call = emplaceCall(packageName, callName, returnType, params)
                delimiter = j + 1
                while (nameAttr.length > delimiter && nameAttr[delimiter] == ' ') delimiter++
                if (delimiter == nameAttr.length) delimiter--
                val annotations = try { parseAnnotations(node as Element) }
                    catch(thr: Throwable) {
                        throw AnnotationParserException("Error while parsing annotations for \"$call\"", thr)
                    }
                annotations.forEach { it.mutableCall = call }
                when (nameAttr[delimiter]) {
                    in '0'..'9' -> {
                        // this is a parameter
                        val n = nameAttr.substring(delimiter).toInt()
                        val param = call.params[n]
                        param.annotations += annotations
                        annotations.forEach {
                            try { it.initialize(n) }
                            catch (thr: Throwable) {
                                throw AnnotationParserException("Error while initializing an annotation functionality" +
                                        " instance $it for parameter #$n of $call", thr)
                            }
                        }
                    }
                    ')' -> {
                        // this is a call
                        call.annotations += annotations
                        annotations.forEach {
                            try { it.initialize(-1) }
                            catch (thr: Throwable) {
                                throw AnnotationParserException("Error while initializing an annotation functionality" +
                                        " instance $it for $call", thr)
                            } }
                    }
                    else -> throw IllegalStateException("Name attribute of the call has invalid format")
                }
            }
        } catch (thr: Throwable) {
            throw AnnotationParserException("Error while parsing \"$file\"", thr)
        }
    }

    override fun toString(): String {
        return root.toString()
    }
}
