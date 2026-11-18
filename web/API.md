# APIs disponibles (resumen para portar pantallas)

## src/domain (import { X } from '../../domain' — hay barrel index.ts)
Planificación: `src/domain/computePlanning.ts` → `usePlanning(): Planning` (= vm.planning), `useMonthIncome(month)`, `computeMonthIncome(db, month)`,
`planningUpdates(p, settings) → { patch, fresh }` (efectos de init de Kotlin: limitSnapshot, circleHistory, logros nuevos → llamar db.setSettings(patch) en un efecto).
Planning = { wealth, forecast, safe, comparison, achievements, data: FinanceData, bankPlan|null, goal|null, goals, goalList, age|null, budget|null, limits: TagLimits, points, level }.

Convenciones: objetos Kotlin → `Foo.bar()`; enums → uniones de texto + `X_VALUES` + `X_INFO` (SpendTag: SPEND_TAG_VALUES / SPEND_TAG_INFO[t].label/.need/.keywords; también Zone, Status, LeakKind, DayMark, LifeGoal, AllocationPreset).
Pair/Triple → tuplas. Propiedades calculadas → campos ya rellenos; usa las factorías makeWealth/makeForecast/makeSafeToSpend/makeBankPlan/makePulse/makeTagLimit/makeFinanceCircle/makeTypicalMonth/makeDebt (para copy() vuelve a llamar a la factoría).
FinanceData: `makeFinanceData({...})` con wealth, savingIds, spending, thisMonth, firstMonth, monthlyIncome, inMonth(), spendingIn(), spentIn(), receivedIn(), closedMonths(n).
Funciones sueltas: spendTag(e), isSaving(a), isDebt(a), signedBalance(a), monthName(ym), byTag(list). `fmt` siempre (v:number)=>string (pasa useMoney().fmt).

- FinanceMath: Wealth.of; InterestMath.monthlyRate/monthlyInterest/project→Projection; IncomeMath.forMonth→MonthIncome, variableAverage, key; Allocation.rebalance/applyPreset; InterestEarned.of(acc, incomes, transfers, today, since)→{earned, estimated, monthlyNow}
- Spend: Classifier.normalize/guess/classify/bucketFor; NaturalEntry.parse(text, history)→ParsedExpense[]; spendTagOf(name)
- Merchants: Merchants.clean/logoUrl/logoUrls; Duplicates.extras
- Planning: Bills.dueDate/isPaid/monthly; Forecasting.forecast/events/dailyVariableSpend; Spending.safeToSpend; BankPlanning.plan/defaultMain/defaultTarget/dailyHabit; Comparison.compare; Achievements.evaluate/level; levelProgress(level, points)
- Insights: Insights.monthStory/describe/describeTimeTravel/timeTravel/netWorthByMonth/earliestMonthsBack/costOfLiving/radar/habits/whatIfs/health(d,fmt)/shareable/owed/simBase/simulate
- Budget: Budgeting.parseLimits/formatLimits/isBillPayment/isFixed/variable/plan→SpendingBudget; SavingsAdvisor.advise; Advisor.today/nextPayday/nextFixedPayday; SavingProfile.STYLES/split/spendFactor/weights; WealthExplainer.explain; LIFE_GOAL_INFO
- Goals: Goals.status/requiredMonthly/monthsTo/paceOf/monthsLeft; GoalList.parse/format/plan/swapImpact; FinancialAging.evaluate
- Alerts: LimitSnapshot.encode/decode; LimitAlerts.check
- Pulse: PulseCalculator.of/info/spendScore/marginFor; zoneOf
- Signals: RecurringDetector.find; RecoverableMoney.find; WeeklyNudge.find; PaymentOrganizer.analyze; PaycheckDistributor.split; TrendDetector.find; RealPriceCalculator.of; daysUntil
- Leaks: InvisibleMoney.find; Anomalies.find; PurchaseSimulator.compare/goalDelayText; monthsBetween
- Circles: FinancialCircles.of/changeText/encodeHistory/decodeHistory/weekAgo
- Debts: Debts.parse/format/parseTricount/formatTricount/balances→FriendBalance[]/people; makeDebt
- CryptoMoves.check/encode/decode · Streaks.of · TodayLeft.spentToday/of/receipt; UpcomingBills.of; upcomingWhenText
- TypicalSpend.of; SubscriptionChecker.of/savingsHealth · WeeklyReader.of · Wrapped.of(d, ym, transfers?) · Stats.of(d, period)/analytics(d, kind)
- CashPlanner.of(d, cushion) · BuyChecker.of · BuzzAnalyzer.analyze(posts, trending, mine?) · Pepe.brief/QUESTIONS · PepeSuggest.of/weekly/weekKey
- PepeBot.answer(question, {d, cash, goal, userName, fmt})/greeting · Assistant.answer/SUGGESTIONS · NewsImpactAnalyzer.analyze/coinsIn/monthlySpend
- PaymentParser.parse(title, text, isWallet)/parseNumber/signature · BizumParser.parse(title, text)
Nota: los meses cortos llevan punto ("5 nov.") igual que Android.

## src/data/services
- actions.ts: refreshCrypto(quiet?), refreshStocks(quiet?), refreshIfStale(), reloadAsset(symbol, id, isCrypto), useRefreshing().
  Hooks → { data, loading, error, reload }: useAssetHistory(key, isCrypto, id, days, cur?) → {times[], values[]}; useOhlc(...) → Candle[]; useCoinStats(id|null, cur?); useExchanges(coinId|null) → CoinMarket[]; useMarkets(ids) → MarketCoin[] (USD); useNews(force?) → NewsItem[]; useBuzz() → BuzzInputs (luego useMemo(BuzzAnalyzer.analyze(data.posts, data.trending, data.mine))).
- prices.ts: CoinResult{id,symbol,name}, StockResult{ticker,name,exchange,type}, MarketCoin{id,symbol,name,price,change24h,spark}, CoinMarket{exchange,pair,url,trusted}, CoinStats{rank,marketCap,volume24h,high24h,low24h,ath,athChangePct,change7d,change30d,change1y,circulating,maxSupply}, Candle{open,high,low,close}, Quote{price,change}.
  cryptoPrices(ids, cur), cryptoQuotes, cryptoHistory, cryptoOhlc, coinStats, coinExchanges, cryptoMarkets, trending(), searchCoins(q), stockPrice(ticker, cur), stockQuote, stockHistory, stockOhlc, searchStocks(q).
- news.ts: NewsItem{title, source, url, epochMs, summary, topic, imageUrl?}, isAd, pageImage(url).
- Acciones y noticias van por proxies CORS públicos: pueden fallar a ratos → mostrar estado de error amable con "Reintentar".
