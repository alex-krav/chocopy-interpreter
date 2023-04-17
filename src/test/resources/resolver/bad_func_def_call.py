def bar() -> int:
    return 1

def foo(x:str, y:bool) -> int:
    return bar()

# All of the below are bad calls
foo("Hello")
foo("Hello", False, 3)
foo("Hello", 3)
foo(1, "Hello")
baz()

