// listens in to the synthdef creation process
JXResourceConstructorCollector {
	classvar store;
	*initClass { store = () }
	*register { |key, con|
		JXTypeCheck(thisMethod, con -> JXResourceConstructor);
		store.includesKey(key).if({ ^Error("key % already registered".format(key)).throw });
		store[key] = con;
	}
	*gobble {
		var out = store.deepCopy();
		JXResourceConstructorCollector.initClass();
		^out;
	}
	*reset { JXResourceConstructorCollector.initClass(); }
}





// THE MAIN CLASSES BEGIN ->


// callable in a synth
JXResourceUgen : JXAbstractClass {
	*registerConstructor { |con|
		JXTypeCheck(thisMethod, con -> JXResourceConstructor);
		JXResourceConstructorCollector.register(con.name, con);
	}
	*createSafeServerControlName {|sym|
		var c = JXTypeCheck(thisMethod, sym -> Symbol);
		^(
			sym.asString
			.reject({|c| [$/, $ ].includes(c) })
			++ "_server_control"
		).asSymbol
	}
}

// used to make resources
JXResourceConstructor : JXAbstractClass {
	var <name;
	*new {|name|
		JXTypeCheck(thisMethod, name -> Symbol);
		^super.newCopyArgs(name)
	}
	createResource { |oscAddr|
		JXTypeCheck(thisMethod, oscAddr -> Symbol);
		^this.subclassResponsibility(thisMethod)
	}
}

// the heart of JX, holds whatever you want, Bus, Buffer...
// is owned by a synth
JXResource : JXAbstractClass {
	var name, <constructor;
	*new {|name, con|
		JXTypeCheck(thisMethod, name -> Symbol, con -> JXResourceConstructor);
		^super.newCopyArgs(name, con)
	}
	prRegisterResource {|jXResourceAccessor|
		JXTypeCheck(thisMethod, jXResourceAccessor -> JXResourceAccessor);
	}
	name { ^name }
	getOwnedResourcePair { |owner| ^JXOwnedResourcePair(owner, this) }
	getArg { ^[] }
	getMapArg { ^[] }
}


// THE MAIN CLASSES END <-

JXAssoc2Array {
	*new {|assoc|
		JXTypeCheck(thisMethod, assoc -> Association);
		^JXAssoc2Array.flatten(assoc)
	}
	*flatten { |a|
		^(case
			{a.isKindOf(Association)}
			{[JXAssoc2Array.flatten(a.key), JXAssoc2Array.flatten(a.value)]}
			{[a]}
		).flatten
	}
}


// ties a JXResource, to some JXResourceAccessor
JXOwnedResourcePair {
	var <owner, <resource;
	*new {|owner, resource|
		JXTypeCheck(thisMethod, owner -> JXResourceAccessor, resource -> JXResource);
		^super.newCopyArgs(owner, resource)
	}
}


JXFunctionConstructor : JXResourceConstructor {
	var <func;
	*new {|name, func|
		JXTypeCheck(thisMethod, name -> Symbol, func -> Function);
		^super.newCopyArgs(name, func)
	}
	createResource { |oscAddr| ^JXFunctionResource(name, this) }
}

JXFunctionResource : JXResource {
	*new {|name, con|
		JXTypeCheck(thisMethod, name -> Symbol, con -> JXFunctionConstructor);
		^super.newCopyArgs(name, con)
	}
	value {|...args| this.constructor.func.(*args) }
}




// used to convert any class to a jxresource
JXAsResource {
	classvar funcStore;
	*initClass {
		funcStore = [];
		JXAsResource.prAdd(JXResource, {|a, nname| a});
		JXAsResource.prAdd(Function, {|a, nname|
			JXTypeCheck(thisMethod, a -> Function, nname -> Symbol);
			JXFunctionResource(nname, JXFunctionConstructor(nname, a))
		});
	}
	*prAdd {|c, func|
		JXTypeCheck(thisMethod, c -> Class, func -> Function);
		funcStore = funcStore ++ (c -> func);
	}
	*new { |v, name|
		JXTypeCheck(thisMethod, name -> [Symbol, Nil]);
		funcStore.do{ |a|
			if(v.isKindOf(a.key), {^a.value.(v, name)})
		}
		^Error("class % not found".format(v.class)).throw
	}
}


// inherited by JXSynth, JXGroup .. etc, basically anything you can call collect on
// These may or may not own the resource

JXResourceAccessor {
	oscAddr { ^this.subclassResponsibility(thisMethod) }
	asNode { ^this.subclassResponsibility(thisMethod) }
	getAllOwnedResources { ^this.subclassResponsibility(thisMethod) }
	getOwnedResource {|sym|
		JXTypeCheck(thisMethod, sym -> Symbol);
		^this.subclassResponsibility(thisMethod)
	}
	getResource {|name|
		JXTypeCheck(thisMethod, name -> Symbol);
		^this.getOwnedResource(name).resource
	}
	prTryAddResource { |sym, r|
		^this.subclassResponsibility(thisMethod)
	}

	at { |...names| ^names.collect( this.atSingular(_) ).flatten }
	atSingular { |name|
		JXTypeCheck(thisMethod, name -> [Symbol, Class]);
		^case
		{ JXTypeQuery(name -> Symbol) }
		{ this.getOwnedResource(name) }

		{ JXTypeQuery(name -> Class) }
		{
			this.getAllOwnedResources().asArray
			.collect(_.resource)
			.select(_.isKindOf(name))
			.collect( JXOwnedResourcePair(this, _) )
			.flatten
		}
	}
	connect {|...assocs|
		assocs.do{|a| JXTypeCheck(thisMethod, a -> Association)};
		JXLongSync({ this.connectForkable( *assocs) });
		^this
	}
	connectForkable {|...assocs|
		var interfaceForConnecting = JXResourceConnectionManagerInterface(
			this.at(_), this.at(_), this, {|...a| JXConnect.makeConnectionOnly(*a) }
		);
		var interfaceForRemaking = JXResourceConnectionManagerInterface(
			this.at(_), this.at(_), this, {|...a| JXConnect.postConnectionCleaup(*a) }
		);
		assocs.do{|a| JXTypeCheck(thisMethod, a -> Association)};
		assocs.do(JXResourceConnectionManager.prRouter(_, interfaceForConnecting));
		assocs.do(JXResourceConnectionManager.prRouter(_, interfaceForRemaking));
	}

	forwardResource { |...assocs|
		assocs.do{ |a|
			case
			{JXTypeQuery(a -> SequenceableCollection)}
			{ a.do(this.forwardResource(_)) }

			{JXTypeQuery(a -> Association)}
			{
				case
				{JXTypeQuery(a.key -> SequenceableCollection)}
				{ a.key.do({|k| this.forwardResource( k -> a.value ) }) }

				{JXTypeQuery(a.key -> JXOwnedResourcePair) && JXTypeQuery(a.value -> Symbol)}
				{ this.prTryAddResource(a.value, a.key ) }

				{ JXTypeCheck(thisMethod,
					a.key -> [SequenceableCollection, JXOwnedResourcePair], a.value -> Symbol
				) }
			}

			{JXTypeQuery(a -> JXResourceAccessor)}
			{
				this.forwardResource(
					*a.getAllOwnedResources.keysValuesDo({|k, v| (k -> v)}).asArray
				)
			}

			{JXTypeQuery(a -> JXResource)}
			{ this.prTryAddResource(a.name, a ) }

			{JXTypeQuery(a -> JXOwnedResourcePair)}
			{ this.prTryAddResource(a.resource.name, a ) }


			{JXTypeCheck(thisMethod,
				a -> [Association, JXResourceAccessor, JXResource, SequenceableCollection])
			}

		}
	}
}










































