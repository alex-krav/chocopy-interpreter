# chocopy-interpreter

An interpreter for [Chocopy](https://chocopy.org/), a subset of Python 3.6 with type annotations and static type checking. This project is just for fun 😂

## Features

Source code is analyzed in a single-pass. However, semantic analysis has two passes over AST: first for definitions and second for statements.

## Requirements
- Java 17
- Maven 3

## Usage
Build a JAR file running command

`mvn clean package`

it will create a **target/chocopy.jar** archive in project directory. Then execute a ChocoPy script by running

`java -jar chocopy.jar FILE.py`

## Test
Test data is taken from [repositories](https://github.com/cs164berkeley) for [CS 164 at UC Berkeley](https://www2.eecs.berkeley.edu/Courses/CS164/), with some additional tests written for more coverage. But the expected results' format was changed from JSON to YAML and node's location data was removed. AST structure was updated to reflect implementation classes, which differ from the reference implementation framework.

Run tests using command

`mvn test`

Only one test **InterpreterTest.testTree** fails with *java.lang.StackOverflowError*. It actually works, which can be checked by enabling it and reducing number of nodes to `n:int = 1` in **tree.py** file.
