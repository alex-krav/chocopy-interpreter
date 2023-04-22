def greet(name:str):
    def display_name():
        print("Hi, " + name)

    display_name()

greet("Oleks")  
