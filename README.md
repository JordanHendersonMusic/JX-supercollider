# JX-supercollider
A Quark for [supercollider](https://supercollider.github.io/) that aims to do four things:

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

Current, JX comes with 3 types of resource:
* busses, `JXIn` and `JXOut`;
* buffers, `JXOwnedBuffer` and `JXBorrowedBuffer`;
* and osc ports, `JXOscSrc` and `JXOscSink`. 

However the class syntax for adding more is designed to be simple.

# Usage
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
## Busses - Reduce and Reshape
`JXIn` and `JXOut` are the built in bus resource.
Whilst `JXOut` takes a name and a signal, `JXIn` has two important options, reduction and reshape.

When conencting busses of with different numbers of channels, a `JXReshape` function will be used.
There are a few builtin options, but custom ones can be provided, 
for example, `JXReshape.circular` will assume the array is a circle around the listener.

When connecting multiple outputs to a single input, a `JXReduce` can be used to determine how these busses should be combined.
There are two builtin options `JXReduce.mean` and `JXReduce.sum`, these are the defaults for control rate and audio rate busses respectively.
Connecting between control rate and audio rate is also allowed.
```supercollider
s.waitForBoot {
	var from = JXSynthDef('/from', {
		JXOut.ar(\outA, SinOsc.ar(2));   // 1 chan
		JXOut.ar(\outB, LFNoise2.ar(2.0.rand!2 + 80)  * 0.5); // 2 chan
		JXOut.ar(\outC, LFNoise2.ar(8.0.rand!6 + 600) * 0.1); // 6 chan
	});

	var to = JXSynthDef('/to', {
		JXIn.kr(\in, 4, JXReduce.mean, JXReshape.circular) // 4 channel bus
	})
	.connect( from[\outA, \outB, \outC] -> \in );

	to.getResource(\in).bus.scope; // scope the bus
}
```
## Buffers 
```supercollider
 s.waitForBoot {
	var writer = JXSynthDef('/writer', {
		var buf = JXOwnedBuffer.fill(\buf, numChannels: 1, duration: 10, func: 0);
		RecordBuf.ar( SoundIn.ar(0), buf );
	});
	
	var grain = JXSynthDef('/grain', {
		var buf = JXBorrowedBuffer(\buf, 1);
		var grains = GrainBuf.ar(2, Dust.ar(40), 0.1, buf, pos: WhiteNoise.ar(), pan: LFNoise2.kr(4));
		JXOut.ar(\out, grains);
	})
	.connect(writer[\buf] -> \buf);
	
	grain.getResource(\out).bus.scope;
	~writer = writer;
}

~writer.getResource(\buf).buffer.plot
```

