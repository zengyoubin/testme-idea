package com.weirddev.testme.intellij.template.context


import org.mockito.Mock
import org.mockito.MockitoAnnotations
import spock.lang.Specification
import spock.lang.Unroll

class MockitoMockBuilderTest extends Specification {

    @Mock
    TestSubjectInspector testSubjectInspector

    MockitoMockBuilder mockitoMockBuilder

    def setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Unroll
    def "get Init Mocks Method where mockitoCoreVersion=#mockitoCoreVersion"() {
        given:
        mockitoMockBuilder = new MockitoMockBuilder(false, false, testSubjectInspector, mockitoCoreVersion)

        expect:
        mockitoMockBuilder.getInitMocksMethod() == expectedResult

        where:
        mockitoCoreVersion || expectedResult
        null               || "initMocks"
        ""                 || "initMocks"
        "1.3.ga"           || "initMocks"
        "1.3.0"            || "initMocks"
        "3.3.0"            || "initMocks"
        "3.3cust.0"        || "initMocks"
        "3.4.0"            || "openMocks"
        "3.4misc.0"        || "openMocks"
        "4.1.0"            || "openMocks"
        "4.1.ga"           || "openMocks"
        "4.6.0"            || "openMocks"
        "4.12.0"           || "openMocks"
        "11.1.0"           || "openMocks"
    }
}

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme