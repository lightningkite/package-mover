package com.lightningkite.packagemover

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.SortedMap
import java.util.jar.JarFile

interface PackageMoverPluginExtension {
    var additional: String
}

class PackageMoverPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        val ext = extensions.create("packageMover", PackageMoverPluginExtension::class.java)
        task("migratePackages") {
            group = "build"
            doLast {
                val replacements = configurations
                    .asSequence()
                    .flatMap { it.files }
                    .distinct()
                    .filter { it.extension == "jar" }
                    .flatMap {
                        JarFile(it).use { j ->
                            j.entries().asIterator().asSequence()
                                .filter { it.name.startsWith("META-INF") && it.name.endsWith(".packagemove") }
                                .flatMap {
                                    j.getInputStream(it).reader().useLines { lines -> lines.linesToImportReplacementPairs() }
                                }
                        }
                    }
                    .associateBy { it.from }
                    .plus(ext.additional.toImportReplacements())
                    .toSortedMap()

                project.rootDir.resolve("src").walkTopDown()
                    .filter { it.extension == "kt" }
                    .forEach {
                        val lines = it.readLines().asSequence()
                        val resultingLines = lines.repairKotlinLines(replacements).joinToString(System.lineSeparator())
                        it.writeText(resultingLines)
                    }
            }
        }
    }
}


fun Sequence<String>.repairKotlinLines(
    replacements: SortedMap<ImportData, MoveDirective>
): Sequence<String> {
    val importData = asSequence()
        .map { it.trim() }
        .filter { it.startsWith("import ") }
        .map { it.removePrefix("import ") }
        .map(ImportData.Companion::parse)
    val repairedImports = importData.repair(replacements)

    val beforeImports = takeWhile { !it.trim().startsWith("import") }
    val afterImports = dropWhile { !it.trim().startsWith("import ") }
        .filter { !it.trim().startsWith("import ") }

    val resultingLines = (beforeImports + repairedImports.map { "import $it" } + afterImports)
    return resultingLines
}

fun String.toImportReplacements() = this.lineSequence().linesToImportReplacementPairs().associateBy { it.from }.toSortedMap()
fun Sequence<String>.linesToImportReplacementPairs(): Sequence<MoveDirective> = this
    .map { it.trim() }
    .filter { it.isNotBlank() && !it.startsWith("//") }
    .map(MoveDirective.Companion::parse)

enum class MoveType {
    Move, Replace
}

data class MoveDirective(
    val from: ImportData,
    val to: ImportData,
    val type: MoveType = MoveType.Move,
) {
    companion object {
        fun parse(it: String): MoveDirective {
            var workOn = it
            var type = MoveType.Replace
            if(it.startsWith("MOVE ", true)) {
                type = MoveType.Move
                workOn = it.substring("MOVE ".length)
            } else if(it.startsWith("REPLACE ", true)) {
                type = MoveType.Replace
                workOn = it.substring("REPLACE ".length)
            }
            return MoveDirective(
                from = workOn.substringBefore(' ').let(ImportData.Companion::parse),
                to = workOn.substringAfterLast(' ').let(ImportData.Companion::parse),
                type = type,
            )
        }
    }
}

data class ImportData(
    val packageName: String,
    val owningElementName: List<String> = listOf(),
    val elementName: String? = null,
    val asName: String? = null
) : Comparable<ImportData> {

    override fun compareTo(other: ImportData): Int = comparator.compare(this, other)

    companion object {
        val comparator = compareBy<ImportData> { it.fqn }.thenBy { it.owningElementName.joinToString(".") }.thenBy { it.elementName }
        fun parse(it: String): ImportData {
            val dots = it.substringBefore(' ').split('.')
            val lower = dots.takeWhile { it.firstOrNull()?.isLowerCase() == true }.filter { it != "*" }
            val after = dots.dropWhile { it.firstOrNull()?.isLowerCase() == true }.filter { it != "*" }
            if (dots.lastOrNull() == "*") {
                // star import
                return ImportData(
                    packageName = lower.joinToString("."),
                    owningElementName = after,
                )
            } else if (after.isNotEmpty()) {
                // Statics import
                return ImportData(
                    packageName = lower.joinToString("."),
                    owningElementName = after.dropLast(1),
                    elementName = after.lastOrNull(),
                    asName = if (it.contains(" as ")) it.substringAfter(" as ") else null
                )
            } else {
                // Normal import
                return ImportData(
                    packageName = lower.dropLast(1).joinToString("."),
                    elementName = lower.lastOrNull(),
                    asName = if (it.contains(" as ")) it.substringAfter(" as ") else null
                )
            }
        }
    }

    override fun toString(): String = buildString {
        append(fqn)
        asName?.let {
            append(" as ")
            append(it)
        }
    }

    val fqn: String
        get() = buildString {
            append(packageName)
            owningElementName.forEach {
                append('.')
                append(it)
            }
            elementName?.let {
                append('.')
                append(it)
            } ?: append(".*")
        }

    fun matches(other: String): Boolean = fqn.startsWith(other)
    fun repair(map: SortedMap<ImportData, MoveDirective>): Set<ImportData> {
        if(this.elementName == null && this.owningElementName.isEmpty()) {
            val submap = map.subMap(this, this.copy(elementName = "~"))
            return setOf(this) + submap.values.map {
                when(it.type) {
                    MoveType.Move -> it.to.copy(elementName = null)
                    MoveType.Replace -> it.to
                }
            }.distinct()
        }
        return map[this]?.to?.let(::setOf) ?: this.let(::setOf)
    }
}
fun Sequence<ImportData>.repair(map: SortedMap<ImportData, MoveDirective>): Sequence<ImportData> = flatMap { it.repair(map) }.distinct()

