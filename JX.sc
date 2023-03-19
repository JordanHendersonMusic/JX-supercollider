// why does this need to exist?
// Well if you try and store in an environmental varible and take too long there will be some odd crash.
// However, if you wrap the call in this, then its fine, I have no idea why though?!
JXLongSync {
	var func, condVar;
	*new { |f|
		JXTypeCheck(thisMethod, f -> Function);
		^super.newCopyArgs(f, CondVar()).();
	}
	value {
		{ func.(); this.release(); }.fork;
		^this.wait
	}
	wait { condVar.wait; Server.default.sync }
	release { condVar.signalAll;  }
}

JXGlobal {
	classvar hasBeenInitBefore;
	classvar store;
	*initClass {
		JXGlobal.reset();
		CmdPeriod.add({ JXGlobal.reset() });
	}
	*reset {
		store = ();
		hasBeenInitBefore = false;
	}

	*new { |...assocs|
		JXAssert(thisMethod, hasBeenInitBefore.not,
			"Cannot define JXGlobal multiple time, please run CmdPeriod to refresh");
		assocs.do({|a| JXTypeCheck(thisMethod, a -> Association) });
		assocs.do({|a| JXTypeCheck(thisMethod, a.key -> Symbol ) });
		hasBeenInitBefore = true;
		store = assocs.asEvent;
	}
	*get { |key|
		JXTypeCheck(thisMethod, key -> Symbol);
		JXAssert(thisMethod, store.includesKey(key),
			"JXGlobal cannot find key \"%\"".format(key));
		^store[key]
	}
	*require { |...assocsOrSymbols|
		assocsOrSymbols.do({ |a|
			JXTypeCheck(thisMethod, a -> [Symbol, Association]);

			case
			{JXTypeQuery(a -> Symbol)}
			{JXGlobal.get(a)}

			{JXTypeQuery(a -> Association)}
			{JXTypeCheck(thisMethod, JXGlobal.get(a.key) -> a.value)}
		});
	}
}


JXOscAddrValidate {
	*new { |sym|
		JXTypeCheck(thisMethod, sym -> Symbol);
		JXAssert(thisMethod, sym.asString[0] == $/,
			"First character of an osc address must be a \"/\""
			" got: %".format(sym)
		);
		JXAssert(thisMethod, sym.asString.includes($ ).not,
			"cannot have spaces in an osc address\n\n"
			" got: %".format(sym)
		);
		^sym
	}
}



JXSynthDef {
	var <synthdefname, <oscAddr, <resourceConstructors;
	*new { |name, func|
		var check = JXTypeCheck(thisMethod, name -> Symbol, func -> Function);
		var osc = JXOscAddrValidate(name);
		var fullOSC = JXActiveOscAddr.push(osc);
		var defname = JXSynthDef.prSynthDefNameSanitize(fullOSC);
		SynthDef(defname, func).add;
		Server.default.sync;
		JXActiveOscAddr.pop();
		^super.newCopyArgs(defname, fullOSC, JXResourceConstructorCollector.gobble()).make();
	}
	make { ^JXSynth(synthdefname, oscAddr, resourceConstructors) }
	*prSynthDefNameSanitize { |name|
		^name.asString
		.reject({|v,i| (i == 0) && (v == $/)}) // remove leading $/
		.reject({|i| [$;, $ ,$:, $,].includes(i)}) // remove bad characters
		.asSymbol
	}
}


JXSynth : JXResourceAccessor {
	var instance, <oscAddr, resources;

	*new { |defname, oscAddr, resourceConstructors|
		var checks = JXTypeCheck(thisMethod,
			defname -> Symbol, oscAddr -> Symbol,
			resourceConstructors -> IdentityDictionary
		);
		var resources = resourceConstructors.collect(
			_.createResource(oscAddr)
		);
		var synth = Synth(
			defName: defname,
			args: resources.collect(_.getArg).asArray.flatten,
			target: JXActiveGroup.get().asTarget,
			addAction: \addToTail
		);
		var sy = Server.default.sync;
		var p = synth.performList(
			\map,
			resources.collect(_.getMapArg).asArray.flatten
		);
		var self = super.newCopyArgs(synth, oscAddr, resources);
		resources.do(_.prRegisterResource(self))
		^self
	}
	asNode { ^instance}
	asTarget{ ^this.asNode.asTarget}

	getAllOwnedResources { ^resources.collect(JXOwnedResourcePair(this, _)) }
	getOwnedResource{ |sym|
		JXTypeCheck(thisMethod, sym -> Symbol);
		JXAssert(thisMethod, resources.includesKey(sym),
			"could not find resource called %".format(sym).asSymbol);
		^JXOwnedResourcePair(this, resources[sym])
	}
	prTryAddResource {|sym, r|
		JXTypeCheck(thisMethod, sym -> Symbol, r -> JXResource);
		JXAssert(thisMethod, resources.includesKey(sym).not,
			"resource % already exists".format(sym).asSymbol);
		resources[sym] = r;
	}
	set {|sym ...args|
		JXTypeCheck(thisMethod, sym -> Symbol);
		instance.set(sym, *args)
	}
}






JXGroup : JXResourceAccessor {
	var instance, <oscAdd, forwardedOwnedResources;
	*new {|osc, func|
		var check = JXTypeCheck(thisMethod, osc -> Symbol, func -> Function);
		var group = Group(JXActiveGroup.get().asTarget, \addToTail);
		var thisosc = JXOscAddrValidate(osc);
		var oscAddr = JXActiveOscAddr.push(thisosc);
		var self = super.newCopyArgs(group, oscAddr, ());
		JXLongSync({ JXActiveGroup.with(self, { func.(self) }) });
		JXActiveOscAddr.pop();
		^self;
	}
	asNode  { ^instance}
	asGroup { ^instance.asGroup}
	asTarget{ ^instance.asTarget}


	getAllOwnedResources { ^forwardedOwnedResources }
	getOwnedResource{ |sym|
		JXTypeCheck(thisMethod, sym -> Symbol);
		JXAssert(thisMethod, forwardedOwnedResources.includesKey(sym),
			"could not find resource called %".format(sym).asSymbol);
		^forwardedOwnedResources[sym]
	}
	prTryAddResource {|sym, r|
		JXTypeCheck(thisMethod, sym -> Symbol, r -> JXOwnedResourcePair);
		JXAssert(thisMethod, forwardedOwnedResources.includesKey(sym).not,
			"resource % already exists".format(sym).asSymbol);
		forwardedOwnedResources[sym] = r;
	}
}




JX2SC : JXResourceAccessor {
	var <group, resourceOwnerSynth, <oscAddr, forwardedOwnedResources;
	*new { |name, owner, func|
		var check = JXTypeCheck(thisMethod,
			name -> Symbol, owner -> Function, func -> Function
		);
		var thisosc = JXOscAddrValidate(name);
		var oscAddr = JXActiveOscAddr.push(thisosc);
		var resourceOwnerSynth = JXSynthDef('/owner', owner);
		var group = JXGroup('/scContext', {});
		var self = super.newCopyArgs(
			group,
			resourceOwnerSynth,
			oscAddr,
			resourceOwnerSynth.getAllOwnedResources()
		);
		JXLongSync({ func.(self) });

		JXActiveOscAddr.pop();
		^self
	}

	asNode { ^group.asNode }
	asGroup { ^group.asGroup }
	asTarget { ^resourceOwnerSynth.asNode }


	getAllOwnedResources { ^forwardedOwnedResources }
	getOwnedResource{ |sym|
		JXTypeCheck(thisMethod, sym -> Symbol);
		JXAssert(thisMethod, forwardedOwnedResources.includesKey(sym),
			"could not find resource called %".format(sym).asSymbol);
		^forwardedOwnedResources[sym]
	}
	prTryAddResource {|sym, r|
		JXTypeCheck(thisMethod, sym -> Symbol);
		JXAssert(thisMethod, forwardedOwnedResources.includesKey(sym).not,
			"resource % already exists".format(sym).asSymbol);
		case
		{JXTypeQuery(r -> JXOwnedResourcePair)}
		{forwardedOwnedResources[sym] = r}
		{JXTypeQuery(r -> JXResource)}
		{forwardedOwnedResources[sym] = JXOwnedResourcePair(resourceOwnerSynth, r)}
	}
}





KeyTest {
	classvar keyTestIdentityStore;
	var symbol;
	*initClass { keyTestIdentityStore = () }

	*new {|sym|
		if(keyTestIdentityStore.includesKey(sym).not, {
			keyTestIdentityStore[sym] = super.newCopyArgs(sym);
			keyTestIdentityStore[sym].freeze;
		});

		^keyTestIdentityStore[sym]
	}
	asSymbol { ^symbol }

	prBinaryOpOnSymbol {|other, opsym|
		^this.asSymbol.perform(opsym, other.asSymbol)
	}
	== {|other| ^this.prBinaryOpOnSymbol(other, '==')}
	!= {|other| ^this.prBinaryOpOnSymbol(other, '!=')}
	==={|other| ^this.prBinaryOpOnSymbol(other, '===')}
	!=={|other| ^this.prBinaryOpOnSymbol(other, '!==')}
	<  {|other| ^this.prBinaryOpOnSymbol(other, '<')}
	>  {|other| ^this.prBinaryOpOnSymbol(other, '>')}
	<= {|other| ^this.prBinaryOpOnSymbol(other, '<=')}
	>= {|other| ^this.prBinaryOpOnSymbol(other, '>=')}

	hash{ ^symbol.hash }
	basicHash{ ^symbol.basicHash }
	identityHash{ ^symbol.identityHash }
}















