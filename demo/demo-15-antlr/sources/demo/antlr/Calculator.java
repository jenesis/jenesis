package demo.antlr;

import demo.antlr.calc.CalcBaseVisitor;
import demo.antlr.calc.CalcLexer;
import demo.antlr.calc.CalcParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class Calculator extends CalcBaseVisitor<Integer> {

    public static void main(String[] args) {
        String expression = args.length == 0 ? "2 * (3 + 4) - 5" : String.join(" ", args);
        System.out.println(expression + " = " + new Calculator().evaluate(expression));
    }

    public int evaluate(String expression) {
        CalcLexer lexer = new CalcLexer(CharStreams.fromString(expression));
        return visit(new CalcParser(new CommonTokenStream(lexer)).calculation());
    }

    @Override
    public Integer visitCalculation(CalcParser.CalculationContext context) {
        return visit(context.expression());
    }

    @Override
    public Integer visitParenthesis(CalcParser.ParenthesisContext context) {
        return visit(context.expression());
    }

    @Override
    public Integer visitProduct(CalcParser.ProductContext context) {
        int left = visit(context.left), right = visit(context.right);
        return context.operator.getType() == CalcParser.TIMES ? left * right : left / right;
    }

    @Override
    public Integer visitSum(CalcParser.SumContext context) {
        int left = visit(context.left), right = visit(context.right);
        return context.operator.getType() == CalcParser.PLUS ? left + right : left - right;
    }

    @Override
    public Integer visitNumber(CalcParser.NumberContext context) {
        return Integer.parseInt(context.NUMBER().getText());
    }
}
