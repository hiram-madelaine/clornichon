# Step Expressions

## Cucumber expressions

A step's sentence matcher is a [cucumber expression](https://github.com/cucumber/cucumber-expressions): `{int}` `{float}` `{word}` `{string}`, optional text `apple(s)`, alternation `hot/cold`. `{number}` is not a cucumber type -- clornichon defines it, so the glues written before still work, and it accepts a sign and decimals.

A literal `/`, `(` or `)` in a sentence must be escaped, or it reads as alternation or optional text:

```clojure
(defthen "the folders {string} \\/ {string} exist" [state a b] ...)
```

## Custom parameter types

`define-parameter-type!` adds a token of your own. The regex says what it captures, the function converts it -- once, instead of in every glue that uses it:

```clojure
(v2/define-parameter-type! "price" #"(\d+\.\d{2}) (EUR|USD)"
  (fn [amount currency] {:amount (bigdec amount) :currency (keyword currency)}))

(defgiven "a book at {price}" [state price]
  ;; "a book at 12.50 EUR" => price is {:amount 12.50M, :currency :EUR}
  (assoc state :price price))
```

The function receives one argument per capture group of the regex, or the whole match when there is none -- make a group non-capturing (`(?:a|b)`) if it is only there to group.

Define the type before loading the glues that use it, at the top of their namespace or in one they require: a sentence using an unknown token raises when its feature is parsed. Defining a type again replaces it.

## Regular expressions

A glue defined with a `#"..."` literal stays a plain regex whatever it contains, matching the whole sentence, and its capture groups become the arguments -- make a group non-capturing (`(?:a|b)`) if it is only there to group. A *string* sentence wrapped in `^...$` or `/.../` is read as a regex too.

## Arguments

One argument per token of the sentence matcher, left to right, after the state -- and each token converts its capture: `{int}` gives a number, `{string}` the text without its quotes (single or double), `{word}` a String. The sentence's own literals are not read: what a step receives is what its tokens capture.

A datatable or a doc string is appended after those, as one more argument: a vector of maps keyed by the header row (keywords, cells as strings) for the table, the string for the doc string.

```gherkin
When I create a product "iphone 6" with properties
  | size | weight |
  | 6    | 2      |
```

```clojure
(defwhen "I create a product {string} with properties" [state name props]
  ;; props => [{:size "6", :weight "2"}]
  ...)
```

## Missing steps

Running a step that matches no glue prints a skeleton with the right expression. Quote the data in the sentence (`"iphone 6"`) and it becomes a `{string}`:

```
Missing step for : When I create a new product with name "iphone 6" and description "awesome phone"
(defwhen "I create a new product with name {string} and description {string}"  [state arg0 arg1]  (do "something"))
```

With Kaocha, `--dry-run` lists every missing step without running anything -- see [Running features](running.md#checking-the-glue-without-running).

## Same sentence, several glues

When a sentence matches glues in several namespaces, the one closest to the feature's namespace wins. Two glues at the same distance raise an error listing both.
