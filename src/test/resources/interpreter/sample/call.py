def h() -> object:
    print("start h")
    print("end h")

def g() -> object:
    print("start g")
    h()
    print("end g")

def f() -> int:
    print("start f")
    g()
    print("end f")
    return 42

print(f())
