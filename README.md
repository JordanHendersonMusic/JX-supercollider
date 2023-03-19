# JX-supercollider
A Quark for [supercollider](https://supercollider.github.io/) that aims to do four things:

* Remove manual resource management by having synths own things like busses and buffers.
* Allow connecting of resources between different synths, vastly simplify signal routing as well as sharing of resources.
* Make working with multiple files easy, safe, and reusable.
* Generally remove some of the nastier edges of supercollider and encourge sustainble coding practices.

Unlike other Quarks[^1], JX is not designed for live coding, or any live alteration of the underlying structure [^2].
Instead, this Quark is designed to aid in making reactive systems, where a performer's actions generate osc data in real-time.

[^1]: [AlgaNode](https://github.com/vitreo12/AlgaLib) is a great alternative if one is looking for a similar system for live coding.
[^2]: there is an exeption to this, where normal supercollider code can be written inside a particular context, see [JX2SC](#jx2sc).


Therefore, JX also comes with a way to work with OSC mapping, where the mappings *themselves* can be sequenced and manipulated.
OSC maps can be considered relationships between the different elements of the system, 
which are then sequenced, blended between, or otherwise manipulated.
These maps can be dynamic, for example mapping some data to a sine wave, applying some incoming data through a low pass filter,
or using supercolliders `Demand` rate ugen to generate sequence to data.
This allows, as a further example, audiovisual relationships to be described in OSC maps, which are then interpolated between as the piece progresses, or in response to some other data.

Current, JX comes with 3 types of resource:
* busses, `JXIn` and `JXOut`;
* buffers, `JXOwnedBuffer` and `JXBorrowedBuffer`;
* and osc ports, `JXOscSrc` and `JXOscSink`. 

However the class syntax for adding more is designed to be simple.





# Usage
Unless otherwise stated, all of the following code should take place inside of a `s.waitForBoot` call.

contents:
* [Connections](#connect)
* [Busses - Reduce and Reshape](#busses---reduce-and-reshape)
* [Buffers](#buffers)
* [Groups and Import - working with multiple files](#groups-and-import---working-with-multiple-files)
* [JX2SC](#jx2sc)
* [OSC mapping](#osc-mapping)
* [Extra utilities](#extra-utilities)
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

`JXOwnedBuffer` can be called with `fill` which will create a collection from the function, if a number is passed in the this will be the default.
Or `fromFile` / `fromFileCwd` can be used to load an audio file.

## Groups and Import - working with multiple files
Building large structures like this can get cumbersome, requiring many connections. 
`JXGroup` can be used encapsulate synths and forward only the require resource outside. 
However, `JXGroup` is best combined with `JXImport` and seperated into a new file.

`main.scd`
```supercollider
JXAssertOpenedFromDirectory("/home/user/my_project_dir");

s.waitForBoot {
	var generators = JXImport.cwd("noises.scd").(
		\groupName: '/noises'
	);
	var outputter = JXSynthDef('/out', {
		JXIn.ar(\in, 2, JXReduce.mean, JXReshape.circular).poll
	})
	.connect( generators[\out] -> \in );

	outputter.getResource(\in).bus.scope;
}
```
`noises.scd`
```supercollider
{   |groupName|
	JXGroup(groupName, { |self|
		var pink = JXSynthDef('/pink', {
			JXOut.ar(\out, PinkNoise.ar(0.1!4))
		});
		var white = JXSynthDef('/white', {
			JXOut.ar(\out, WhiteNoise.ar(0.3!4))
		});

		var sum = JXSynthDef('/sum', {
			var in = JXIn.ar(\in, 2);
			JXOut.ar(\out, in);
		})
		.connect(
			pink[\out]  ->  \in,
			white[\out] ->  \in
		);

		self.forwardResource( sum[\out] -> \out );
	});
}
```
This allow many of these groups to be made and any information passed in.
```supercollider
var n1 = JXImport.cwd("noises.scd").(\groupName: '/noisesA');
var n2 = JXImport.cwd("noises.scd").(\groupName: '/noisesB');
var n3 = JXImport.cwd("noises.scd").(\groupName: '/noisesC');
```
## JX2SC
It is impossible to use patterns with a JX synth.
However, `JX2SC` can be used to create a wrapper around supercollider, so that any supercollider process looks like a JX one.
The `owner` argument defines all the jx resources.
The `func` argument is where the normal supercollider code is placed, and `context` is passed it.
With `context` you can get the `owner`'s resources and the group.
```supercollider

var noise = JXSynthDef('/noise', { JXOut.kr(\out, LFNoise2.kr(0.3)) });

var bleeps = JX2SC('/bleeps',
	owner: {
		JXIn.kr(\amp, 1, JXReduce.mean);
		JXIn.kr(\freq, 1, JXReduce.mean);
		JXOut.ar(\out, DC.ar(0!2));
	},
	func: { |context|
		SynthDef(\bleeper, {
			var sin = SinOsc.ar(\freq.kr) * EnvGen.ar(Env.perc, doneAction: 2);
			var out = sin * context.getResource(\amp).asUgen;
			context.getResource(\out).asUgen(out!2);
		}).add;
		s.sync;

		Pdef(\pat, Pbind(
			\instrument, \bleeper,
			\group, context.asGroup, /// this line is very important!!!
			\dur, 0.2,
			\freq, context.getResource(\freq).asPattern.linexp(-1, 1, 200, 800)
		)).play
})
.connect( noise[\out] -> [\amp, \freq] );

bleeps.getResource(\out).bus.scope
```

## OSC mapping

OSC mapping is used to create relationships between data sources and sinks.
For an example, in an audiovisual one such map might be, as the saxophone's mic recieves a louder signal, make the screen brighter.
These maps can then be manipulated.

### Defining osc information

Internal osc information is defined inside of synths

```supercollider
JXSynthDef('/src', {
	var sig = ....;
	JXOscSrc.kr('/amp', Amplitude.kr(sig, 0.3, 0.1) )
});
JXSynthDef('/sink', {
	var amp = JXOscSink.kr('/amp', 1);
	...
});
```
The osc addresses of these sinks and sources, will be: `/src/amp` and `/sink/amp`.
**The server's node tree mirrors the osc namespace**. 
Therefore placing both these is a `JXGroup` called `/group` would give the osc addresses: `/group/src/amp` and `/group/sink/amp`.

External osc information is declared like this...
```supercollider
JXOscStore.registerExternalSinks(
	NetAddr("localhost", 23425) -> [
		(osc: '/pd/freq', chans: 1),
		(osc: '/pd/amp',  chans: 2),
	]
);
JXOscStore.registerExternalSrc(
	NetAddr("localhost", 5623) -> [
		(osc: '/pd/value', chans: 1),
		(osc: '/pd/xy', chans: 2)
	]
);
```
It is best to place these inside of a seperate file and pass in the network addresses.

## `JXOscMapMk`

To make an osc map, use `JXOscMapMk`.
```supercollider
var mapAmaker = JXOscMapMk({ |srcs|
	JXOscMap((
		'/beep/gain' : srcs['/noise/amp'] * LFNoise2.kr(2),
		'/beep/amp' : -10.dbamp
	))
});
```
Then inside of a special synthdef, `JXOscMapperSynth`, the osc sources can be gathered, the map created, passed to `JXOscMapOutput`, and an `JXOscRelay` created sending the external osc data to its respective `NetAddr`.
```supercollider
JXOscMapperSynth({
	var srcs = JXOscStore.getMapSources();
	var mapa = mapAmaker.makeMap(srcs)
	JXOscMapOutput.kr(mapa);
});

JXOscRelay.init(sendingRate: 120);
```

## Mutating and Sequencing `JXOscMap`s

There are two types of osc map interpolation that do subtly different things depending of whether the keys in the map are the same.

### `JXOscMap` Interpolation
When an osc sink is present in one map, but not the following, 
the interpolation classer (e.g., `JXOscMapLinSelectX` or `JXOscMapBiLinearX`)
will gradually apply a lowpass filter to the data until it stops moving.
This often works well when interpolating slowly, but can cause weird behaviour when suddenly jumping between different maps.


A lineary interpolation between any number of maps using `JXOscMapLinSelectX`.
```supercollider
JXOscMapperSynth({|src|
	var a = mapAmk.makeMap(src);
	var b = mapBmk.makeMap(src);
	var c = mapCmk.makeMap(src);
	var d = mapDmk.makeMap(src);

	var lerp = JXOscMapLinSelectX(MouseX.kr(0, 3), a, b, c, d);
	
	JXOscMapOutput.kr(lerp);
});
```

`JXOscMapBiLinearX` does 2d interpolation, like an 'x-y' pad.
```supercollider
JXOscMapperSynth({|src|
	var a = mapAmk.makeMap(src);
	var b = mapBmk.makeMap(src);
	var c = mapCmk.makeMap(src);
	var d = mapDmk.makeMap(src);
	
	var coords = [MouseX.kr, MouseY.kr];
	var lerp = JXOscMapBiLinearX(coords, a, b, c, d);
	
	JXOscMapOutput.kr(lerp);
});
```
### `JXOscMap` operators
The other way to mutate maps is by using the built in operators. 
However, the operator will only be applied when both osc maps share the key, otherwise, the result will just copy the value.

`<+/>` is a short hand for the average.
```supercollider
JXOscMapperSynth({|src|
	var a = mapAmk.makeMap(src);
	var b = mapBmk.makeMap(src);
	var c = mapCmk.makeMap(src);
	var d = mapDmk.makeMap(src);
	
	var out = (a + b) <+/> (c + d); 
	
	JXOscMapOutput.kr(out);
});
```

### Composability

It is not possible to get acess to an osc source inside of a `JXOscMapperSynth`.

However, `JXOscMapMk` is composable inside a `JXOscMapMk`.
```supercollider

var mapCmaker = JXOscMapMk({ |srcs|
	var a = mapAmaker.makeMap(srcs);
	var b = mapBmaker.makeMap(srcs);

	JXOscMapLinSelectX(srcs['/beep/out/amp'], a, b)
	++                                               // concat maps
	JXOscMap(( '/beep/freq' : LFNoise0.kr(0.1) ))
});
```


## Extra utilities

### `JXGlobal`
'Global' varibles are best avoided when using JX. 
If information needs to be passed to a seperate file, it is best to use a function argument with `JXImport`.
However, if there is some information that doesn't change throughout a performance, `JXGlobal` can be used to safetly pass immutable data around.
The benifit over global varibles is that this will throw if it doesn't exist, 
you can explicitly state what varibles a file needs at the top, 
and you can *only* define them once.
This makes it clear to any user of the file that they must define these globals and if they don't, there is an immediate readable error.
```supercollider
JXGlobal(
	\testSound -> WhiteNoise.ar(),
	\numSpeakers -> 6,
	\mainOut -> { |a| Out.ar(0, a) }
);

JXGlobal.get(\thisWillThrow) // throws an error as it doesn't exist

// will throw an error if these do not exist and they don't match the required indicated types, where present.
JXGlobal.require(
	\numSpeakers -> Integer, 
	\mainOut -> Function, 
	\testSound
);
```

### `JXTypeCheck`
Type checks a varible, will throw an error and stop execution.
```supercollider
JXTypeCheck(thisMethod, a -> Integer);
```
### `JXAssertOpenedFromDirectory`
Will force the user to open the file from a given directory. 
I have seen several issues where File.getcwd doesn't return what a user expects, this will force the issue.
```supercollider
JXAssertOpenedFromDirectory("/path/to/project/directory");
```

