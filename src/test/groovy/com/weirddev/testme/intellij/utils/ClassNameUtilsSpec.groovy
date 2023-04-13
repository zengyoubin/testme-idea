package com.weirddev.testme.intellij.utils

import spock.lang.*

class ClassNameUtilsSpec extends Specification {


    @Unroll
    def "stripGenerics"() {
        when:
        def res = ClassNameUtils.stripGenerics(canonicalName)
        then:
        res == result

        where:
        canonicalName               | result
        "java.util.Set"             | "java.util.Set1"
        "java.util.Set<Fire>"       | "java.util.Set1"
        "java.util.Set<List<Fire>>" | "java.util.Set1"
    }
    @Unroll
    def "test"() {
        when:
        def s = source
        then:
        s == expectd
        where:
        source || expectd
        "1"    || "2"

    }

    def "extractGenerics"() {

        expect:
        ClassNameUtils.extractGenerics(canonicalName) == result

        where:
        canonicalName               | result
        "java.util.Set"             | ""
        "java.util.Set<Fire>"       | "<Fire>"
        "java.util.Set<List<Fire>>" | "<List<Fire>>"
    }

    @Unroll
    def "array Dimensions where canonicalName=#canonicalName then expect: #expectedResult"() {
        expect:
        ClassNameUtils.arrayDimensions(canonicalName) == expectedResult

        where:
        canonicalName            || expectedResult
        "canonicalName"          || 0
        "java.lang.String[]"     || 1
        "java.lang.String[][]"   || 2
        "java.lang.String[][][]" || 3
    }

}

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme