# Cloutjure

This is a whitepaper for the design of a multi-source karma and
community value/trust measurement. By combining multi-pass analysis
with a mult-source approach to data storage this proposal attempts to
present a simple, extensible framework for building a multi-factor
reputation system resiliant to various gaming strategies.

## Architecture

	Username :- String
	
	Long     :- java.lang.Long
	
	Config   :- {modules : (Seq Module)
		         server  : Backend}

	----------------------------------------------------------------------------

	cloutjure/score       Config Username -> Long
        Maps a username and a config to a user's "karma" score

	cloutjure/add-module  Config Module Float -> Config
		Adds a scoring module to the parameter configuration. The
        additional Float parameter is a scaling constant intended for
        use in normalizing modules scores up and down.


	cloutjure.mod.irc.thanks/score Backend Username -> Long
	    Computes a score based on the number of times a user has been
        thanked in IRC. The number of times that a user has said thank
        you is also factored in, but is less valuable.

	cloutjure.mod.irc.thanks/add! Backend Username Msgid -> None
	    Records a "new" message into the implementation score
        persistence structure. Invoked for datastore side-effects
        whenever new traffic is recorded.

	----------------------------------------------------------------------------
	
	cloutjure.mod.irc.msgs/score   Backend Username -> Long
	    Computes a score based on the total number of messages a user
        has sent in IRC. Note that this is a naive all-time-messages
        module and makes no attempt to implement karma decay due to
        inactivity.

	cloutjure.mod.irc.msgs/add! Backend Username Msgid -> None
		Updates a users's msgs score for datastore side-effects.

	----------------------------------------------------------------------------

	cloutjure.mod.irc.karma/score  Backend Username -> Long
		Reports the user's Lazybot karma. This is expected to
        correlate more or less linearly with the above metrics of
        thanks count and msg count, but is proposed for implementation
        on the off chance that it does add more information.

	cloutjure.mod.irc.karma/add! Backend Username Msgid -> None
		Updates the user's lazybot karma for datastore side-effects.

	----------------------------------------------------------------------------

	cloutjure.mod.irc.age/score    Backend Username -> Long
		Reports a function of the number of days which the user has
        been active. This naive count is likely to be reduced by log2
        or log10 so that karma is not gained linearly for every active
        day however this is considered an implementation and tuning
        detail.

	cloutjure.mod.irc.age/add! Backend Username Msgid -> None
		Updages the user's activity lifespan for datastore
        side-effects.

The basic architecture in support of this symbol structure is a
multi-table datastore, featuring a single
