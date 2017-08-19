package sirgl

class CompletionTest : CompletionTestBase() {
    fun `test completion`() { // TODO tests, for now just playing around
        doSingleCompletion("""
        class String{
            private static int length(){return 0;}
            private static int foo(String s){return 0;}
        }
        class List<T>{}
        class A{
            public static void abc(String a){}
            public static void def(List<String> a){}
        }
        class Main { public static void main(String[] args, List<String> a, int c, String s) {s.fo/*caret*/()}}
""", """
        class A{public static void abc(){}}
        class Main { public static void main(String[] args) {abc(args[0])/*caret*/()}}
""")
    }
}