def bar() -> int:
    return 1

def foo(x:str, y:bool) -> int:
    return bar()

foo("Hello", False)
