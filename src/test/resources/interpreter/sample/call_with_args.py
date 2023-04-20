def h(msg: str) -> object:
    print(msg)

def g(y:int, z:int) -> object:
    print("start g")
    print(y)
    print(z)
    h("h")
    print("end g")

def f(x:int) -> int:
    print("start f")
    print(x)
    g(1, x)
    print("end f")
    return x

print(f(4))
