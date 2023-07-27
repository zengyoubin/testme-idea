package com.weirddev.testme.intellij.generator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.Map;

/**
 * @author yb_zeng
 * @date 2023-07-25
 */
public class CommonConstant {
    public static final Map<String, String> grReplacementTypesStatic;
    public static final Map<String, String> grDefaultTypeValues;

    static {
        String grReplacementTypesStaticStr = """
                {
                    "java.util.Collection": "[<VAL>]",
                    "java.util.Deque": "new LinkedList([<VAL>])",
                    "java.util.List": "[<VAL>]",
                    "java.util.Map": "[<VAL>:<VAL>]",
                    "java.util.NavigableMap": "new java.util.TreeMap([<VAL>:<VAL>])",
                    "java.util.NavigableSet": "new java.util.TreeSet([<VAL>])",
                    "java.util.Queue": "new java.util.LinkedList<TYPES>([<VAL>])",
                    "java.util.RandomAccess": "new java.util.Vector([<VAL>])",
                    "java.util.Set": "[<VAL>] as java.util.Set<TYPES>",
                    "java.util.SortedSet": "[<VAL>] as java.util.SortedSet<TYPES>",
                    "java.util.LinkedList": "new java.util.LinkedList<TYPES>([<VAL>])",
                    "java.util.ArrayList": "[<VAL>]",
                    "java.util.HashMap": "[<VAL>:<VAL>]",
                    "java.util.TreeMap": "new java.util.TreeMap<TYPES>([<VAL>:<VAL>])",
                    "java.util.LinkedList": "new java.util.LinkedList<TYPES>([<VAL>])",
                    "java.util.Vector": "new java.util.Vector([<VAL>])",
                    "java.util.HashSet": "[<VAL>] as java.util.HashSet",
                    "java.util.Stack": "new java.util.Stack<TYPES>(){{push(<VAL>)}}",
                    "java.util.LinkedHashMap": "[<VAL>:<VAL>]",
                    "java.util.TreeSet": "[<VAL>] as java.util.TreeSet"
                }""";
        String grDefaultTypeValuesStr = """
                {
                    "byte": "(byte)0",
                    "short": "(short)0",
                    "int": "0",
                    "long": "0L",
                    "float": "0F",
                    "double": "0D",
                    "char": "(char)'a'",
                    "boolean": "true",
                    "java.lang.Byte": "java.lang.Byte.parse(\\"0\\")",
                    "java.lang.Short": "(short)0",
                    "java.lang.Integer": "0",
                    "java.lang.Long": "1L",
                    "java.lang.Float": "1.1F",
                    "java.lang.Double": "0d",
                    "java.lang.Character": "'a' as Character",
                    "java.lang.Boolean": "Boolean.TRUE",
                    "java.math.BigDecimal": "java.math.BigDecimal.ZERO",
                    "java.math.BigInteger": "0g",
                    "java.util.Date": "new java.util.Date()",
                    "java.util.Calendar":"java.util.Calendar.getInstance()",
                    "java.sql.Timestamp": "new java.sql.Timestamp(java.lang.System.currentTimeMillis())",
                    "java.time.LocalDate": "java.time.LocalDate.now()",
                    "java.time.LocalDateTime":"java.time.LocalDateTime.now()",
                    "java.time.LocalTime": "java.time.LocalTime.now()",
                    "java.time.Instant":"java. time.LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC)",
                    "java.lang.Class": "Class.forName(\\"\\")"
                    }
                """;
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            grReplacementTypesStatic = objectMapper.readValue(grReplacementTypesStaticStr, new TypeReference<>() {
            });
            grDefaultTypeValues = objectMapper.readValue(grDefaultTypeValuesStr, new TypeReference<>() {
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
