package demo.errorprone;

public class Comparison {

    public static void main(String[] args) {
        String built = String.join("", "jen", "esis");
        System.out.println("\"jenesis\" == built  -> " + new Comparison().same("jenesis", built));
        System.out.println("\"jenesis\".equals(built) -> " + "jenesis".equals(built));
    }

    public boolean same(String left, String right) {
        return left == right;
    }
}
