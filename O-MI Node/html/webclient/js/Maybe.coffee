###
Like java, javascript is optimized for throwing null pointer exceptions
coffeescript helps with its series of ? operators
but here is a scala option-eqsue "maybe monad" when such semantics
are preferred over the ? operators or when you want your data
to self-document its indefinite-ness (regular vars don't warn you
that you may get null or undefined!).

note that we're perfectly happy to give you a Some(null) or a Some(undefined)
just like scala. if you have an x that you want to fmap to None if it's
null or undefined then use Maybe(x) which will otherwise produce a Some(x)

Note: scala chose to name fmap 'map' and bind 'flatMap'
  I mention that because fmap is not short for flatMap - a possible confusion.
###

maybeProto = {
  alert: () -> alert @toString()
  log: () -> console.log @toString()
  __isMaybe: true
}

sp = someProto = Object.create(maybeProto)
sp.isDefined = sp.nonEmpty = true
sp.isEmpty = false
sp.toString = () -> "Some(#{@__v})"
sp.fmap = (f) -> Maybe(f(@__v))
sp.bind = (f) -> f(@__v)
sp.exists = sp.forall = sp.bind
sp.join = () -> if @__v.__isMaybe? then @__v else throw "already flat!"
sp.getOrElse = sp.get = () -> @__v
sp.toArray = () -> [@__v]

Some = (value) ->
  some = Object.create(someProto)
  some.__v = value
  some

#There's only ever one instance of None!
None = Object.create(maybeProto)
None.isDefined = None.nonEmpty = false
None.isEmpty = true
None.toString = () -> "None"
None.fmap = None.bind = None.join = () -> None #nice and toxic!
None.exists = (f) -> false
None.forall = (f) -> true
None.get = () -> throw "called get on none!"
None.getOrElse = (x) -> x
None.toArray = () -> []

Maybe = (value) ->
  if value? then Some(value) else None

Maybe.empty = None

Maybe.fromArray = (arr) ->
  if not (arr instanceof Array) then throw "array expected"
  else switch arr.length
    when 0 then None
    when 1 then Some(arr[0])
    else throw "array length must be 0 or 1"

#flattens Array of maybes into maybe an array
#if any of the elements are None then the result will be None
Maybe.flatten = (arrOfMaybes) ->
  step = (args, arr) -> switch args.length
    when 0 then None
    #ugly shifts, but I can promise to only call once!
    #I wont let shift hit the fan..
    when 1 then args.shift().fmap (x) -> arr.concat [x]
    else args.shift().bind (x) -> step(args, arr.concat [x])
  step(arrOfMaybes, [])

# usage:
# Maybe.all(aOpt, bOpt, cOpt, dOpt, eOpt)((a, b, c, d, e) -> 
#   a+b+c+d+e #result
# )
# returns Some(result) if all args are Some(x) else None
Maybe.all = (args...) -> (f) ->
  Maybe.flatten(args).fmap (arr) -> f(arr...)

#export
[@None, @Some, @Maybe] = [None, Some, Maybe]
