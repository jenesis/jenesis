grammar Calc;

calculation : expression EOF ;

expression
    : OPEN expression CLOSE                                          # Parenthesis
    | left=expression operator=( TIMES | DIVIDE ) right=expression   # Product
    | left=expression operator=( PLUS | MINUS ) right=expression     # Sum
    | NUMBER                                                         # Number
    ;

PLUS : '+' ;
MINUS : '-' ;
TIMES : '*' ;
DIVIDE : '/' ;
OPEN : '(' ;
CLOSE : ')' ;
NUMBER : [0-9]+ ;
WHITESPACE : [ \t\r\n]+ -> skip ;
