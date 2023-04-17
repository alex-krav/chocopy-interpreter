x:int = 0

def set_x() -> int:
    global x
    x = 1
    return x

set_x()
print(x)
