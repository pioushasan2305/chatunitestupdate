Greetings! Thank you for assisting me in crafting the unit test. Your expertise is invaluable in generating test cases targeting specific line(s)-to-test. Let's begin by introducing the line to be tested along with its dependencies. Then, detailed instructions will follow for generating the test case. Finally, examples will illustrate how to utilize the line(s)-to-test and compose corresponding test cases.

### Line(s)-to-test && Dependencies

**Introduction of line(s)-to-test inside the method (${method_sig}) and Focal Class (${class_name})**

The line(s)-to-test is inside the ${method_sig}, resides in the focal class ${class_name}.

Here is the source code of the focal class, including member methods and fields. The full implementation of the method where the line(s)-to-test is will be provided, along with summarizations and signatures of other member methods.

The complete code provided here is for reference only and is not intended for generating unit tests.
```java
${full_fm}
```
The exact line(s)-to-test within ${class_name}.${method_name} are:
```java
${lines_to_test}
```
<#-- List of dependent classes and their brief information -->
<#list c_deps as key, value>
    Brief information about the dependent class ${key} is as follows:
    ```
    ${value}
    ```
</#list>

<#-- List of dependent methods and their brief information -->
<#list m_deps as key, value>
    Brief information about the dependent method ${key} is as follows:
    ```
    ${value}
    ```
</#list>
Based on the information provided above, generate unit tests for code ${step_code}. The description of this code is ${step_desp}

Now please generate a whole unit test file for the line(s)-to-test.

#### Requirements and Attention for the Unit Test to Generate:

- Ensure that the unit tests are executable: they should run without any compilation errors, runtime errors, or timeouts.
- Aim for comprehensive coverage: the unit tests should encompass a significant portion of the codebase, including instructions and branches within the line(s)-to-test.
- Avoid altering the method and line(s) under test.
- Generate a complete unit test file, including the package declaration and all imports.
- Import all dependent libraries used in the unit test file.
- Name the test class as ${class_name}_Test.
- Ensure that the unit test methods do execute the line(s) under test.
- Target exact line(s)-to-test provided above.
- Utilize appropriate tools and adhere to the language style guidelines:
- Utilize JUnit 4 for testing.
- Adhere to Java 8 language style conventions.


### Output Format:

Here are my requirements for your output format:

<generate>
    The whole unit test file is:
    ```java
    ...
    ```
</generate>

Now, armed with this information, please proceed with the generation following my instructions.
You MUST finish all generation in ONE RESPONSE!
You MUST FULLY write ALL test methods!
You shouldn't leave any spare work for the human! Finish everything!
```