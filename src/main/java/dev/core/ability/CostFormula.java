package dev.core.ability;

import dev.core.entity.RPGEntity;
import dev.core.stat.StatType;

/**
 * A parsed arithmetic formula for a dynamic ability cost, authored as a string
 * in abilities.yml:
 *
 * <pre>{@code
 * cost:
 *   mode: MANA
 *   formula: "10 + 0.05 * MANA_MAX"
 * }</pre>
 *
 * Grammar (standard precedence, left-associative):
 *
 * <pre>
 * expr    := term (('+' | '-') term)*
 * term    := unary (('*' | '/') unary)*
 * unary   := '-' unary | primary
 * primary := NUMBER | IDENT | '(' expr ')'
 * </pre>
 *
 * Identifiers resolve against the casting entity at evaluation time: any
 * {@link StatType} name (case-insensitive) yields that stat's current value,
 * and {@code MANA_MISSING} / {@code HEALTH_MISSING} yield max pool minus
 * current pool. Unknown identifiers are rejected with a descriptive
 * {@link IllegalArgumentException}. The string is parsed once at config-load
 * time; {@link #evaluate(RPGEntity)} only walks the tree.
 */
public final class CostFormula {

	private final String source;
	private final Node root;

	private CostFormula(String source, Node root) {
		this.source = source;
		this.root = root;
	}

	/**
	 * Parses a formula string. Throws {@link IllegalArgumentException} on empty
	 * input, unexpected characters, or truncated/malformed expressions so bad
	 * config fails loudly at load time.
	 */
	public static CostFormula parse(String source) {
		if (source == null || source.isBlank()) {
			throw new IllegalArgumentException("Cost formula is empty");
		}
		Parser parser = new Parser(source);
		Node root = parser.parseExpression();
		parser.expectEnd();
		return new CostFormula(source, root);
	}

	public String getSource() {
		return source;
	}

	/**
	 * Evaluates the formula against the caster's current stats.
	 *
	 * @throws IllegalArgumentException when the formula references an unknown
	 *                                  variable or evaluates to a non-finite
	 *                                  value (e.g. division by zero)
	 */
	public double evaluate(RPGEntity caster) {
		double value = root.evaluate(caster);
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("Cost formula '" + source + "' evaluated to a non-finite value");
		}
		return value;
	}

	@Override
	public String toString() {
		return source;
	}

	// ------------------------------------------------------------- AST

	private sealed interface Node permits NumberNode, VariableNode, NegateNode, BinaryNode {
		double evaluate(RPGEntity caster);
	}

	private record NumberNode(double value) implements Node {
		@Override
		public double evaluate(RPGEntity caster) {
			return value;
		}
	}

	private record VariableNode(String name, String source) implements Node {
		@Override
		public double evaluate(RPGEntity caster) {
			return resolveVariable(name, source, caster);
		}
	}

	private record NegateNode(Node inner) implements Node {
		@Override
		public double evaluate(RPGEntity caster) {
			return -inner.evaluate(caster);
		}
	}

	private record BinaryNode(char operator, Node left, Node right) implements Node {
		@Override
		public double evaluate(RPGEntity caster) {
			return switch (operator) {
			case '+' -> left.evaluate(caster) + right.evaluate(caster);
			case '-' -> left.evaluate(caster) - right.evaluate(caster);
			case '*' -> left.evaluate(caster) * right.evaluate(caster);
			case '/' -> left.evaluate(caster) / right.evaluate(caster);
			default -> throw new IllegalStateException("Unknown operator: " + operator);
			};
		}
	}

	/**
	 * Resolves an identifier against the caster: stat names yield the stat's
	 * current value; MANA_MISSING / HEALTH_MISSING yield max pool minus current
	 * pool. Case-insensitive.
	 */
	private static double resolveVariable(String name, String source, RPGEntity caster) {
		if (caster == null) {
			throw new IllegalArgumentException(
					"Cost formula variable '" + name + "' needs a caster to resolve against");
		}
		String key = name.toUpperCase();
		long now = System.currentTimeMillis();
		switch (key) {
		case "MANA_MISSING":
			return caster.getStatEngineAdapter().getCurrentValue(StatType.MANA_MAX, now)
					- caster.getStatEngineAdapter().getCurrentValue(StatType.MANA_RESOURCE, now);
		case "HEALTH_MISSING":
			return caster.getStatEngineAdapter().getCurrentValue(StatType.HEALTH_MAX, now)
					- caster.getStatEngineAdapter().getCurrentValue(StatType.HEALTH_RESOURCE, now);
		default:
			try {
				StatType type = StatType.valueOf(key);
				return caster.getStatEngineAdapter().getCurrentValue(type, now);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Unknown variable '" + name + "' in cost formula '" + source + "'");
			}
		}
	}

	// ------------------------------------------------------------- parser

	private static final class Parser {
		private final String source;
		private int pos;

		private Parser(String source) {
			this.source = source;
		}

		private Node parseExpression() {
			Node node = parseTerm();
			while (true) {
				char c = peek();
				if (c == '+' || c == '-') {
					pos++;
					node = new BinaryNode(c, node, parseTerm());
				} else {
					break;
				}
			}
			return node;
		}

		private Node parseTerm() {
			Node node = parseUnary();
			while (true) {
				char c = peek();
				if (c == '*' || c == '/') {
					pos++;
					node = new BinaryNode(c, node, parseUnary());
				} else {
					break;
				}
			}
			return node;
		}

		private Node parseUnary() {
			skipWhitespace();
			if (peek() == '-') {
				pos++;
				return new NegateNode(parseUnary());
			}
			return parsePrimary();
		}

		private Node parsePrimary() {
			skipWhitespace();
			char c = peek();
			if (c == '(') {
				pos++;
				Node inner = parseExpression();
				skipWhitespace();
				if (peek() != ')') {
					throw error("expected ')'");
				}
				pos++;
				return inner;
			}
			if (Character.isDigit(c)) {
				return parseNumber();
			}
			if (Character.isLetter(c) || c == '_') {
				return new VariableNode(parseIdentifier(), source);
			}
			if (c == '\0') {
				throw error("unexpected end of formula");
			}
			throw error("unexpected character '" + c + "'");
		}

		private Node parseNumber() {
			int start = pos;
			while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
				pos++;
			}
			String number = source.substring(start, pos);
			try {
				return new NumberNode(Double.parseDouble(number));
			} catch (NumberFormatException e) {
				throw error("invalid number '" + number + "'");
			}
		}

		private String parseIdentifier() {
			int start = pos;
			while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
				pos++;
			}
			return source.substring(start, pos);
		}

		private void expectEnd() {
			skipWhitespace();
			if (pos < source.length()) {
				throw error("unexpected trailing input");
			}
		}

		/**
		 * Next non-whitespace character, or {@code '\0'} at the end of input
		 * (loop conditions break on it; primary parsing reports it as an error).
		 */
		private char peek() {
			skipWhitespace();
			return pos < source.length() ? source.charAt(pos) : '\0';
		}

		private void skipWhitespace() {
			while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
				pos++;
			}
		}

		private IllegalArgumentException error(String message) {
			return new IllegalArgumentException("Bad cost formula '" + source + "': " + message);
		}
	}
}
