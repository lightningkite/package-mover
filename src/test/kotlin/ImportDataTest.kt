package com.lightningkite.packagemover

import kotlin.test.*

class ImportDataTest {
    @Test fun testParseStar() {
        assertEquals(
            ImportData("com.test"),
            ImportData.parse("com.test.*")
        )
    }
    @Test fun testParseClass() {
        assertEquals(
            ImportData("com.test", elementName = "Test"),
            ImportData.parse("com.test.Test")
        )
    }
    @Test fun testParseExtension() {
        assertEquals(
            ImportData("com.test", elementName = "test"),
            ImportData.parse("com.test.test")
        )
    }
    @Test fun testParseClassAlias() {
        assertEquals(
            ImportData("com.test", elementName = "Test", asName = "X"),
            ImportData.parse("com.test.Test as X")
        )
    }
    @Test fun testParseExtensionAlias() {
        assertEquals(
            ImportData("com.test", elementName = "test", asName = "X"),
            ImportData.parse("com.test.test as X")
        )
    }
    @Test fun testParseObjectMembers() {
        assertEquals(
            ImportData("com.test", owningElementName = listOf("Test")),
            ImportData.parse("com.test.Test.*")
        )
    }
    @Test fun testParseObjectMember() {
        assertEquals(
            ImportData("com.test", owningElementName = listOf("Test"), elementName = "test"),
            ImportData.parse("com.test.Test.test")
        )
    }
    @Test fun testToString() {
        listOf(
            "com.test.*",
            "com.test.Test",
            "com.test.test",
            "com.test.Test as X",
            "com.test.test as X",
            "com.test.Test.*",
            "com.test.Test.test",
        ).forEach {
            assertEquals(
                ImportData.parse(it).toString(),
                it
            )
        }
    }

    @Test fun testRepairMapParse() {
        assertEquals(
            """
                // Comment
                MOVE com.old.Test -> com.new.Test        
                move com.old.test moved to com.new.test
                REPLACE com.old.stillExists -> com.new.stillExists
                com.old.stillExists2 -> com.new.stillExists2
            """.toImportReplacements(),
            listOf(
                MoveDirective(ImportData.parse("com.old.Test"), ImportData.parse("com.new.Test"), MoveType.Move),
                MoveDirective(ImportData.parse("com.old.test"), ImportData.parse("com.new.test"), MoveType.Move),
                MoveDirective(ImportData.parse("com.old.stillExists"), ImportData.parse("com.new.stillExists"), MoveType.Replace),
                MoveDirective(ImportData.parse("com.old.stillExists2"), ImportData.parse("com.new.stillExists2"), MoveType.Replace),
            ).associateBy { it.from }.toSortedMap()
        )
    }

    val sampleRepairMap = """
        // Comment
        MOVE com.old.Test -> com.new.Test        
        MOVE com.old.test moved to com.new.test
        REPLACE com.old.stillExists -> com.new.stillExists
    """.toImportReplacements()

    @Test fun testRepairBasic() {
        assertEquals(
            ImportData.parse("com.old.Test").repair(sampleRepairMap),
            setOf(ImportData.parse("com.new.Test"))
        )
    }
    @Test fun testRepairBasic2() {
        assertEquals(
            ImportData.parse("com.old.test").repair(sampleRepairMap),
            setOf(ImportData.parse("com.new.test"))
        )
    }
    @Test fun testRepairBasic3() {
        assertEquals(
            ImportData.parse("com.old.stillExists").repair(sampleRepairMap),
            setOf(ImportData.parse("com.new.stillExists"))
        )
    }
    @Test fun testRepairFullPackage() {
        assertEquals(
            sequenceOf(
                "com.old.*",
            ).map(ImportData.Companion::parse).repair(sampleRepairMap).toSet(),
            sequenceOf(
                "com.old.*",
                "com.new.*",
                "com.new.stillExists",
            ).map(ImportData.Companion::parse).toSet()
        )
    }

    @Test fun testFixFile() {
        assertEquals(
            """
                @file:UseContextualSerialization()
                package somepackage
                import com.old.*
                
                fun sample() {
                }
            """.trimIndent().lines().asSequence().repairKotlinLines(sampleRepairMap).joinToString("\n"),
            """
                @file:UseContextualSerialization()
                package somepackage
                import com.new.*
                import com.new.stillExists
                import com.old.*
                
                fun sample() {
                }
            """.trimIndent()
        )
    }
}