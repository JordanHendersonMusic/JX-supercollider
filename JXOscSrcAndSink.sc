JXOscSrc : JXResourceUgen {
	*kr { |oscbase, data|
		var check = JXTypeCheck(thisMethod, oscbase -> Symbol);
		var realoscbase = JXOscAddrValidate(oscbase);
		var busSelectorName = JXResourceUgen.createSafeServerControlName(oscbase);
		JXOscSrc.registerConstructor(JXOscSrcConstructor(realoscbase, data.numChannels, busSelectorName));
		^ReplaceOut.kr(busSelectorName.kr(999), data)
	}
}

JXOscSrcConstructor : JXResourceConstructor {
	var <numChannels, <busSelectorName;
	*new {|oscBase, numChannels, busSelectorName|
		JXTypeCheck(thisMethod,
			oscBase -> Symbol,
			numChannels -> Integer,
			busSelectorName -> Symbol
		);
		^super.newCopyArgs(oscBase, numChannels, busSelectorName)
	}
	createResource {|oscAddr|
		var checks = JXTypeCheck(thisMethod, oscAddr -> Symbol);
		^JXOscSrcResource(name, this, (oscAddr ++ name).asSymbol, Bus.control(Server.default, numChannels));
	}
}

JXOscSrcResource : JXResource {
	var <oscAddr, <bus;
	*new {|name, con, oscAddr, bus|
		JXTypeCheck(thisMethod,
			name -> Symbol,
			con -> JXResourceConstructor,
			oscAddr -> Symbol,
			bus -> Bus
		);
		^super.newCopyArgs(name, con, oscAddr, bus);
	}
	prRegisterResource{ |jXResourceAccessor|
		JXTypeCheck(thisMethod, jXResourceAccessor -> JXResourceAccessor);
		JXOscStore.add(JXOscPortIntSrc(oscAddr, bus, this.constructor.numChannels))
	}
	getArg { ^[this.constructor.busSelectorName, this.bus] }

	asUgen {
		In.kr(this.bus, this.constructor.numChannels)
	}

}


JXOscSink : JXResourceUgen {
	*kr{ |oscbase, numChannels|
		var check = JXTypeCheck(thisMethod, oscbase -> Symbol, numChannels -> Integer);
		var realoscbase = JXOscAddrValidate(oscbase);
		var ccname = JXResourceUgen.createSafeServerControlName(realoscbase.asSymbol);
		JXOscSink.registerConstructor(JXOscSinkConstructor(realoscbase.asSymbol, numChannels, ccname ));
		^ccname.kr(0!numChannels);
	}
}

JXOscSinkConstructor : JXResourceConstructor {
	var <numChannels, <controlName;
	*new { |oscbase, numChannels, controlName|
		JXTypeCheck(thisMethod, oscbase -> Symbol, numChannels -> Integer, controlName -> Symbol);
		^super.newCopyArgs(oscbase, numChannels, controlName);
	}
	createResource { |oscAddr|
		var checks = JXTypeCheck(thisMethod, oscAddr -> Symbol);
		^JXOscSinkResource(name, this, (oscAddr ++ name).asSymbol, Bus.control(Server.default, numChannels));
	}
}

JXOscSinkResource : JXResource {
	var <oscAddr, <bus;
	*new {|name, con, oscAddr, bus|
		JXTypeCheck(thisMethod,
			name -> Symbol,
			con -> JXResourceConstructor,
			oscAddr -> Symbol,
			bus -> Bus
		);
		^super.newCopyArgs(name, con, oscAddr, bus);
	}
	prRegisterResource{ |jXResourceAccessor|
		JXTypeCheck(thisMethod, jXResourceAccessor -> JXResourceAccessor);
		JXOscStore.add(JXOscPortIntSink(oscAddr, bus, this.constructor.numChannels) )
	}
	getArg    { ^[this.constructor.controlName, this.bus.asMap] }
	getMapArg { ^[this.constructor.controlName, this.bus] }
	asUgen { |d| Out.kr(this.bus, d) }
}









