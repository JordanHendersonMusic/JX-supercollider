# JX-supercollider
A Quark for supercollider that aims to do four things:

* Remove manual resource management by having synths own things like busses and buffers.
* Allow connecting of resources between different synths, vastly simplify signal routing as well as sharing of resources.
* Make working with multiple files easy, safe, and reusable.
* Generally remove some of the nastier edges of supercollider and encourge sustainble coding practices.

Unlike other Quarks[^1], JX is not designed for live coding, or any live alteration of the underlying structure [^2].
Instead, this Quark is designed to aid in making reactive systems, where a performer's actions generate osc data in real-time.

[^1]: [AlgaNode](https://github.com/vitreo12/AlgaLib) is a great alternative if one is looking for a similar system for live coding.
[^2]: there is an exeption to this, where normal supercollider code can be written inside a particular context, see JX2SC.


Therefore, JX also comes with a way to work with OSC mapping, where the mappings *themselves* can be sequenced and manipulated.
Osc maps can then be considered as relationships between the different elements of the system, 
which are then sequenced, blended between, or otherwise manipulated.

# Examples
## Connect

```supercollider
s.waitForBoot {
	var from = JXSynthDef('/from', {
		JXOut.ar(\out, SinOsc.ar(2));   
	});

	var to = JXSynthDef('/to', {
		JXIn.ar(\in, 1)
	})
	.connect( from[\out] -> \in );  
}
```

`JXIn` and `JXOut` are both busses.
When connect is called, a new synth is create that will map them together.

`.connect` has a few extra syntaxes
```supercollider
// Many 2 one connections
.connect(
	from[\outA] -> \in
	from[\outB] -> \in,
	from[\outC] -> \in,
);
// or like this
.connect( from[\outA, \outB, \outC] -> \in );

// one to many...
.connect( from[\outA] -> [\inA, \inB] );

// many to many - creates 6 connections
.connect( from[\outA, \outB, \outC] -> [\inA, \inB] );

// with a scaling function
.connect( from[\outA] -> _.exprange(200, 400) -> \freq );
```

