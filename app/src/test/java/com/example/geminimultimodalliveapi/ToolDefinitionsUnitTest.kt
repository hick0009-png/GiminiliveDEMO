package com.example.geminimultimodalliveapi

import com.example.geminimultimodalliveapi.network.ToolDefinitions
import org.junit.Assert.*
import org.junit.Test

class ToolDefinitionsUnitTest {

    @Test
    fun allSchemaTypesAreLowercase() {
        val tools = ToolDefinitions.getTools()
        val json = tools.toString(2)
        val upper = listOf("\"OBJECT\"", "\"STRING\"", "\"INTEGER\"", "\"ARRAY\"", "\"NUMBER\"", "\"BOOLEAN\"")
        upper.forEach { assertFalse("Found uppercase type: $it", json.contains(it)) }
    }

    @Test
    fun allParametersHaveObjectType() {
        val funcs = ToolDefinitions.getTools().getJSONObject(0).getJSONArray("functionDeclarations")
        for (i in 0 until funcs.length()) {
            val params = funcs.getJSONObject(i).getJSONObject("parameters")
            assertEquals("Function #$i param type", "object", params.getString("type"))
        }
    }

    @Test
    fun allPropertyTypesAreLowercase() {
        val funcs = ToolDefinitions.getTools().getJSONObject(0).getJSONArray("functionDeclarations")
        for (i in 0 until funcs.length()) {
            val params = funcs.getJSONObject(i).optJSONObject("parameters") ?: continue
            val props = params.optJSONObject("properties") ?: continue
            for (key in props.keySet()) {
                val t = props.getJSONObject(key).getString("type")
                assertTrue("$key in func #$i has uppercase type: $t", t.all { it.isLowerCase() })
            }
        }
    }

    @Test
    fun allFunctionsHaveUniqueNames() {
        val funcs = ToolDefinitions.getTools().getJSONObject(0).getJSONArray("functionDeclarations")
        val names = mutableSetOf<String>()
        for (i in 0 until funcs.length()) {
            val name = funcs.getJSONObject(i).getString("name")
            assertTrue("Duplicate name: $name", names.add(name))
        }
        assertTrue("Should have >= 18 functions", names.size >= 18)
    }

    @Test
    fun allRequiredParametersHaveCorrespondingProperties() {
        val funcs = ToolDefinitions.getTools().getJSONObject(0).getJSONArray("functionDeclarations")
        for (i in 0 until funcs.length()) {
            val f = funcs.getJSONObject(i)
            val params = f.optJSONObject("parameters") ?: continue
            val props = params.optJSONObject("properties") ?: JSONObject()
            val required = params.optJSONArray("required") ?: continue
            for (j in 0 until required.length()) {
                val r = required.getString(j)
                assertTrue("$r is required but missing from properties in ${f.getString("name")}", props.has(r))
            }
        }
    }
}
