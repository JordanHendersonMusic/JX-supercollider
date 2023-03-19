JXOscPortBase {
	var <oscAddr, <bus, <numChannels;
	*new { |oscAddr, bus, numChannels|
		JXTypeCheck(thisMethod, oscAddr -> Symbol, bus -> Bus, numChannels -> Integer);
		^super.newCopyArgs(oscAddr, bus, numChannels);
	}
	inUgen { 	^In.kr(this.bus, this.numChannels) 	}
	outUgen { |d|
		// for some reason this isn't working?
		JXAssert(thisMethod, d.numChannels == this.numChannels,
			"Channel mismatch, expected %, got %".format(this.numChannels, d.numChannels));
		^ReplaceOut.kr(this.bus, d)
	}
}




JXOscPortSrcBase : JXOscPortBase {
	*new { |oscAddr, bus, numChannels|
		JXTypeCheck(thisMethod, oscAddr -> Symbol, bus -> Bus, numChannels -> Integer);
		^super.newCopyArgs(oscAddr, bus, numChannels);
	}
}
JXOscPortSinkBase : JXOscPortBase {
	*new { |oscAddr, bus, numChannels|
		JXTypeCheck(thisMethod, oscAddr -> Symbol, bus -> Bus, numChannels -> Integer);
		^super.newCopyArgs(oscAddr, bus, numChannels);
	}
}





JXOscPortIntSrc : JXOscPortSrcBase {
	*new {|oscAddr, bus, numChannels|
		JXTypeCheck(thisMethod, oscAddr -> Symbol, bus -> Bus, numChannels -> Integer);
		^super.newCopyArgs(oscAddr, bus, numChannels)
	}
}
JXOscPortExtSrc : JXOscPortSrcBase {
	var <netAddr;
	*new { |oscAddr, bus, numChannels, netAddr|
		JXTypeCheck(thisMethod, oscAddr -> Symbol, bus -> Bus, numChannels -> Integer, netAddr -> NetAddr);
		^super.newCopyArgs(oscAddr, bus, numChannels, netAddr)
	}
}


JXOscPortIntSink : JXOscPortSinkBase {
	*new {|oscAddr, bus, numChannels|
		JXTypeCheck(thisMethod, oscAddr -> Symbol, bus -> Bus, numChannels -> Integer);
		^super.newCopyArgs(oscAddr, bus, numChannels)
	}
}
JXOscPortExtSink : JXOscPortSinkBase {
	var <netAddr;
	*new { |oscAddr, bus, numChannels, netAddr|
		JXTypeCheck(thisMethod, oscAddr -> Symbol, bus -> Bus, numChannels -> Integer, netAddr -> NetAddr);
		^super.newCopyArgs(oscAddr, bus, numChannels, netAddr)
	}
}






JXOscStore {
	classvar <ports;
	*reset { ports = ()	}
	*initClass {
		JXOscStore.reset();
		CmdPeriod.add({ JXOscStore.reset() })
	}
	*registerExternalSinks { |...addrs|
		addrs.do( JXOscStore.prRegister(thisMethod.name, {|oscAddr, netAddr, numChannels|
			JXOscPortExtSink(oscAddr, Bus.control(Server.default, numChannels), numChannels, netAddr)
		}, _) );
	}
	*registerExternalSrc { |...addrs|
		addrs.do( JXOscStore.prRegister(thisMethod.name, {|oscAddr, netAddr, numChannels|
			JXOscPortExtSrc(oscAddr, Bus.control(Server.default, numChannels), numChannels, netAddr)
		}, _) );
	}

	*prRegister { |methodName, portMakerFunc, addr|
		JXTypeCheck(thisMethod, portMakerFunc -> Function, methodName -> Symbol);
		JXTypeCheck(thisMethod, addr -> Association);
		JXTypeCheck(thisMethod, addr.key -> NetAddr);
		JXAssert(thisMethod, Server.default.hasBooted,
			"Server must be booted before registering osc ports");

		case
		{ JXTypeQuery(addr.value -> SequenceableCollection)}
		{
			addr.value.do({ |e|
				JXTypeCheck(thisMethod, e -> IdentityDictionary);
				JXAssert(thisMethod, e[\osc].isNil.not && e[\chans].isNil.not,
					"Must have an Event with keys \osc & \chans, got instead %".format(e));
				JXOscStore.add( portMakerFunc.(
					oscAddr: e[\osc], netAddr: addr.key, numChannels: e[\chans]
				))
			});
		}

		{ JXTypeQuery(addr.value -> IdentityDictionary)}
		{
			JXAssert(thisMethod, addr.value[\osc].isNil.not && addr.value[\chans].isNil.not,
				"Must have an Event with keys \osc & \chans, got instead %".format(addr.value));
			JXOscStore.add( portMakerFunc.(
				oscAddr: addr.value[\osc] , netAddr: addr.key, numChannels: addr.value[\chans]
			))
		}

		{ JXOscStore.prRegisterHelp(methodName) }
	}
	*prRegisterHelp { |methodName|
		"JXOscStore.% has recieved incorrect arguments"
		"\narguments should be formatted like this:"
		"\nJXOscStore.%("
		"\n   someNetAddr -> (osc: '/some/osc/addr', chans: 1), "
		"\n   // or "
		"\n   someNetAddr -> ["
		"\n      (osc: '/some/osc/addr', chans: 1),"
		"\n      (osc: '/someother/osc/addr', chans: 2)"
		"\n   ]"
		"\n)".format(methodName, methodName).warn
	}

	*add { |port|
		JXTypeCheck(thisMethod, port -> JXOscPortBase);
		JXAssert(thisMethod, JXOscStore.ports.includesKey(port.oscAddr).not,
			"Osc port already registered, oscAddr = %".format(port.oscAddr));
		JXOscStore.ports[port.oscAddr] = port;
	}
	*getAllOfClass {|class|
		JXTypeCheck(thisMethod, class -> Class);
		^JXOscStore.ports.select({|p| JXTypeQuery(p -> class) })
	}
	*getByOscAddr { |oscAddr|
		var c = JXTypeCheck(thisMethod, oscAddr -> Symbol);
		^JXOscStore.ports[oscAddr]
	}
	*hasOscAddr {|oscAddr|
		^ JXOscStore.ports.includesKey(oscAddr)
	}
	*getAllOscAddrs { ^JXOscStore.ports.keys }

	*prPortNotFoundError {|n|
		^"Could not find osc port %".format(n.asSymbol)
	}

	*getMapSources {
		// why do these have to be split? I don't know.
		var c = JXOscStore.getAllOfClass(JXOscPortSrcBase).collect(_.inUgen);
		var o = JXOscMapSources(c);
		^o
	}
}

JXOscMap {
	var <store;
	*new {|ev|
		var check1 = JXTypeCheck(thisMethod, ev -> IdentityDictionary);

		var check2 = ev.keys.asArray.do({|osc|
			var check = JXTypeCheck(thisMethod, osc -> Symbol );
			JXOscAddrValidate(osc);
			JXAssert(
				thisMethod,
				JXOscStore.hasOscAddr(osc),
				JXOscMap.prCouldNotFindKeyError(osc)
			)
		});
		var osckeys = ev.collect({|v,k|
			( k : v )
		})
		.inject((), JXFnOp('++'));

		JXOscMapperSynth.assertInside(thisMethod);

		^super.newCopyArgs(osckeys);
	}

	blend { |other, l|
		JXTypeCheck(thisMethod, other -> JXOscMap);
		^JXOscMap(store.blend(other.store, l))
	}

	+ { |other| ^merge(other, JXFnOp('+')) }
	- { |other| ^merge(other, JXFnOp('-')) }
	* { |other| ^merge(other, JXFnOp('*')) }
	/ { |other| ^merge(other, JXFnOp('/')) }
	++ { |other| ^JXOscMap( store ++ other.store ) }

	<+/> {|other|
		^this.average(other)
	}
	average {|other|
		^this.merge(other, {|l,r| (l+r)/2})
	}

	merge {|other, func|
		JXTypeCheck(thisMethod, other -> JXOscMap);
		^JXOscMap(store.merge(other.store, func, fill: true));
	}
	at { |oscAddr|
		JXTypeCheck(thisMethod, oscAddr -> Symbol);
		JXAssert(thisMethod, store.includesKey(oscAddr),
			JXOscMap.prCouldNotFindKeyError(oscAddr)
		);
		^store[oscAddr]
	}
	keys { ^store.keys }
	atOr { |oscAddr, or|
		JXTypeCheck(thisMethod, oscAddr -> Symbol);
		^try {this.at(oscAddr)} {or}
	}
	*prCouldNotFindKeyError { |key|
		^"Could not find oscAddr \"%\""
		"\n\nMake sure the mapping happens after the synths and external osc ports have been declared."
		"\n\nHere is a list of all osc addresses: \n\n%"
		.format(key.asSymbol, JXOscStore.getAllOscAddrs().collect(_.asSymbol))
	}
}

JXOscMapSources : JXOscMap {

}

JXOscMapMk {
	var func;
	*new { |func|
		JXTypeCheck(thisMethod, func -> Function);
		JXAssert(thisMethod, func.def.argNames.size == 1,
			"The function to JXOscMapObj must have one input argument that takes the OSC srcs "
			"which is addressable like this: src['/some/osc/addr']. "
			"Further, the function must return an Event with osc-sink addresses as the keys "
			"and some form of ugen data as the value."
		);
		^super.newCopyArgs(func);
	}
	makeMap { |srcs|
		var check = JXTypeCheck(thisMethod, srcs -> JXOscMapSources);
		var out = func.(srcs);
		JXAssert(thisMethod, out.isKindOf(JXOscMap),
			"JXOscMapMk must return an instance of JXOscMap, got %".format(out.class))
		^out;
	}
}


JXOscMapCombinerBase {
	*getDefaults {|...maps|
		var check = maps.do({|m| JXTypeCheck(thisMethod, m -> JXOscMap )});
		var keys = maps.collect({|m| m.keys.collect(_.asSymbol).asArray });
		var all_keys = keys.inject([], { |old, n| old ++ n }).asSet.asArray;

		^all_keys.collect({ |key|
			(key : Ramp.kr(JXOscStore.getByOscAddr(key).inUgen(), 1))
		}).reduce('++');
	}

}

JXOscMapLinSelectX : JXOscMapCombinerBase {
	*new {|lerp ...maps|
		var mapCheck = maps.do{ |m| JXTypeCheck(thisMethod, m -> JXOscMap ) };
		var sinkDefault = JXOscMapCombinerBase.getDefaults(*maps);

		var hjlkas = sinkDefault.postln;
		var result = sinkDefault.collect({ |default, osc|
			LinSelectX.kr(lerp, maps.collect(_.atOr(osc, default)));
		});
		^JXOscMap(result);
	}
}



JXOscMapBiLinearX : JXOscMapCombinerBase {
	*new{ |xy, topLeft, topRight, bottomLeft, bottomRight|
		var c1 = JXTypeCheck(thisMethod,
			topLeft -> JXOscMap, topRight -> JXOscMap,
			bottomLeft -> JXOscMap, bottomRight -> JXOscMap);
		var c2 = JXAssert(thisMethod, xy.numChannels == 2,
			"JXOscMapBiLinearX must have an array of 2 values, respresenting the x and y");

		var sinkDefault = JXOscMapCombinerBase.getDefaults(topLeft, topRight, bottomLeft, bottomRight);

		var result = sinkDefault.collect({|default, k|
			JXOscMapBiLinearX.prBiLinUgen(
				xy,
				topLeft.atOr(k, default),
				topRight.atOr(k, default),
				bottomLeft.atOr(k, default),
				bottomRight.atOr(k, default)
			)
		});

		^JXOscMap(result);
	}

	*prBiLinUgen { |xy, topLeft, topRight, bottomLeft, bottomRight|
		var x = xy[0], y = xy[1];
		^(topLeft * (1-x) * (1-y)) +
		(topRight * x * (1-y)) +
		(bottomLeft * (1-x) * y) +
		(bottomRight * x * y)
	}
}


JXOscMapOutput {
	classvar <>hasBeenCalledBefore;
	*initClass {
		JXOscMapOutput.reset();
		CmdPeriod.add({ JXOscMapOutput.reset() });
	}
	*reset { JXOscMapOutput.hasBeenCalledBefore = false; }
	*kr { |mapResult|
		var v = JXAssert(thisMethod, JXOscMapOutput.hasBeenCalledBefore.not,
			"Cannot have more than one call to JXOscMapOutput, call CmdPeriod to r");
		var c = JXTypeCheck(thisMethod, mapResult -> JXOscMap);
		var sinks = JXOscStore.getAllOfClass(JXOscPortSinkBase);
		var sinkDefaults = sinks.collect(_.inUgen);
		JXOscMapperSynth.assertInside(thisMethod);
		sinks.keysValuesDo{ |oscAddr, sink|
			var r = mapResult.atOr(oscAddr, sinkDefaults[oscAddr]);
			ReplaceOut.kr(sink.bus, r);
		}
	}
}


JXOscMapperSynth {
	classvar instance;
	classvar <insideOscMapperSynth;
	*initClass {
		JXOscMapperSynth.reset();
		CmdPeriod.add({ JXOscMapperSynth.reset() })
	}
	*reset{
		instance = nil;
		insideOscMapperSynth = false;
	}
	*assertInside {|method|
		JXAssert(method, insideOscMapperSynth,
			"Cannot create a JXOscMap outside of a JXOscMapperSynth"
		);
	}
	*new {|func|
		JXAssert(thisMethod, instance.isNil,
			"It is not possible to make more than one JXOscMapperSynth. Please run CmdPeriod to refresh");

		insideOscMapperSynth = true;
		instance = JXSynthDef('/jxOscMapperSynth', func);
		insideOscMapperSynth = false;

		JXAssert(thisMethod, instance.getAllOwnedResources.size == 0,
			"JXOscMapperSynth cannot create any JXResources (e.g., JXIn, JXOscSink...)."
			"\nIf you are trying to control the combination of JXOscMaps with an osc address, "
			"please wrap this inside a JXOscMapMk"
		);

		^instance
	}
}

JXOscRelay {
	classvar sinkSender, hasBeenRun;
	*initClass {
		JXOscRelay.stop();
		CmdPeriod.add({ JXOscRelay.stop() })
	}
	*stop {
		try {sinkSender.stop}{};
		sinkSender = nil;
		hasBeenRun = false;
	}
	*init { |sendingRate|
		var check = JXAssert(thisMethod, hasBeenRun.not,
			"There can only be one JXOscRelay, try running CmdPeriod"
		);
		var srcs = JXOscStore.getAllOfClass(JXOscPortExtSrc);
		var sinks = JXOscStore.getAllOfClass(JXOscPortExtSink);
		var sinkNetAddrs = sinks.collect(_.netAddr).asSet.asArray;

		var srcFuncs = srcs.collect({
			|src|
			OSCFunc({ |msg|
				src.bus.set(*msg[1..])
			}, src.oscAddr, recvPort: src.netAddr.port);
		});

		sinkSender = Routine({
			loop {
				sendingRate.reciprocal.wait;
				sinks.do({|s|
					s.bus.get({ |v| s.netAddr.sendMsg(s.oscAddr, *v) })
				});
			}
		}).play;
		^JXOscRelay;
	}
}























































