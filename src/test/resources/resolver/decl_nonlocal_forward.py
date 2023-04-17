def outer() -> int:
    x:int = 0
    def inner() -> int:
        nonlocal x
        x = 1
        return x
    inner()
    return x

print(outer())
