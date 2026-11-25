import type {
  CareerTierDef,
  CareerTrackDef,
  CountryDef,
  EducationProgramDef,
  EraPack,
  GameState,
  InvestmentHolding,
  SkillId,
} from './types';
import { clamp, SeededRng } from './rng';
import { getAgeYears, getDt, getMonthsPerTurn, scaleAnnualProbability, scaleCompoundFactor } from './time';
import { getCountry } from './eraLoader';

export function calculateAnnualIncomeTax(annualIncome: number, country: CountryDef): number {
  if (annualIncome <= 0) return 0;
  let tax = 0;
  let prevCap = 0;
  for (const bracket of country.taxBrackets) {
    const cap = bracket.upTo ?? Infinity;
    if (annualIncome <= prevCap) break;
    const taxableInBracket = Math.min(annualIncome, cap) - prevCap;
    tax += taxableInBracket * bracket.rate;
    prevCap = cap;
  }
  return Math.max(0, Math.round(tax));
}

export function calculateNetWorth(state: GameState, pack: EraPack): number {
  const investmentTotal = state.player.finance.investments.reduce((sum, h) => sum + h.balance, 0);
  const housing = pack.assets.housing.find((h) => h.id === state.player.finance.housingId);
  const country = getCountry(pack, state.config.countryId);
  const homeValue =
    state.player.finance.ownsHousing && housing
      ? Math.round(housing.purchasePrice * country.costOfLivingMultiplier)
      : 0;
  const debts = state.player.finance.mortgageBalance + state.player.education.studentLoanBalance;
  return Math.round(state.player.finance.cash + investmentTotal + homeValue - debts);
}

export function findCareerTier(
  pack: EraPack,
  trackId: string,
  tierId: string,
): { track: CareerTrackDef; tier: CareerTierDef } | null {
  const track = pack.careers.find((t) => t.id === trackId);
  if (!track) return null;
  const tier = track.tiers.find((ti) => ti.id === tierId);
  if (!tier) return null;
  return { track, tier };
}

export function getRequiredWorkOrSchoolHours(state: GameState, pack: EraPack): number {
  let hours = 0;
  if (state.player.education.currentProgramId) {
    const prog = pack.education.find((e) => e.id === state.player.education.currentProgramId);
    if (prog) hours += prog.weeklyStudyHoursRequired;
  }
  if (state.player.job) {
    const found = findCareerTier(pack, state.player.job.trackId, state.player.job.tierId);
    if (found) hours += found.tier.weeklyHoursRequired;
  }
  return Math.min(70, hours);
}

export function canEnrollInProgram(
  state: GameState,
  program: EducationProgramDef,
): { eligible: boolean; reason?: string } {
  const ageYears = getAgeYears(state.player.ageMonths);
  if (state.player.education.completedProgramIds.includes(program.id)) {
    return { eligible: false, reason: 'Already completed this program.' };
  }
  if (state.player.education.currentProgramId) {
    return { eligible: false, reason: 'Already enrolled in a program.' };
  }
  if (ageYears < program.minAge) {
    return { eligible: false, reason: `Minimum age is ${program.minAge}.` };
  }
  if (program.prerequisite && !state.player.education.completedProgramIds.includes(program.prerequisite)) {
    return { eligible: false, reason: `Requires prerequisite: ${program.prerequisite}.` };
  }
  if (state.player.stats.intelligence < program.minIntelligence) {
    return { eligible: false, reason: `Requires Intelligence ${program.minIntelligence}+.` };
  }
  return { eligible: true };
}

export function enrollInEducation(
  state: GameState,
  programId: string,
  pack: EraPack,
): { state: GameState; success: boolean; message: string } {
  const program = pack.education.find((e) => e.id === programId);
  if (!program) return { state, success: false, message: 'Education program not found.' };

  const check = canEnrollInProgram(state, program);
  if (!check.eligible) {
    return { state, success: false, message: check.reason ?? 'Not eligible.' };
  }

  return {
    state: {
      ...state,
      player: {
        ...state.player,
        education: {
          ...state.player.education,
          currentProgramId: program.id,
          monthsCompletedInProgram: 0,
        },
      },
    },
    success: true,
    message: `Enrolled in ${program.name}.`,
  };
}

export function canApplyForJob(
  state: GameState,
  track: CareerTrackDef,
  tier: CareerTierDef,
): { eligible: boolean; reason?: string } {
  const ageYears = getAgeYears(state.player.ageMonths);
  if (ageYears < tier.minAge) {
    return { eligible: false, reason: `Minimum age is ${tier.minAge}.` };
  }
  if (tier.minIntelligence && state.player.stats.intelligence < tier.minIntelligence) {
    return { eligible: false, reason: `Requires Intelligence ${tier.minIntelligence}+.` };
  }
  if (tier.requiredEducation.length > 0) {
    const hasReq = tier.requiredEducation.some((ed) =>
      state.player.education.completedProgramIds.includes(ed),
    );
    if (!hasReq) {
      return { eligible: false, reason: `Requires one of: ${tier.requiredEducation.join(', ')}.` };
    }
  }
  for (const [skill, minVal] of Object.entries(tier.minSkills)) {
    const current = state.player.skills[skill as SkillId] ?? 0;
    if (current < (minVal ?? 0)) {
      return { eligible: false, reason: `Requires ${skill} ${minVal}+ (have ${Math.round(current)}).` };
    }
  }
  if (tier.minYearsInTrack && (!state.player.job || state.player.job.trackId !== track.id)) {
    return { eligible: false, reason: `Requires ${tier.minYearsInTrack} years experience in ${track.name}.` };
  }
  return { eligible: true };
}

export function applyForJob(
  state: GameState,
  trackId: string,
  tierId: string,
  pack: EraPack,
  rng: SeededRng,
): { state: GameState; success: boolean; message: string } {
  const found = findCareerTier(pack, trackId, tierId);
  if (!found) return { state, success: false, message: 'Job not found.' };

  const check = canApplyForJob(state, found.track, found.tier);
  if (!check.eligible) {
    return { state, success: false, message: check.reason ?? 'Not qualified.' };
  }

  const macro = pack.meta.macroStates[state.world.macroState];
  const country = getCountry(pack, state.config.countryId);
  const interviewChance = clamp(
    (0.55 + state.player.stats.charisma * 0.003 + state.player.stats.intelligence * 0.002) *
      macro.hiringMultiplier,
    0.2,
    0.96,
  );

  if (!rng.chance(interviewChance)) {
    return {
      state,
      success: false,
      message: `You interviewed for ${found.tier.title}, but they went with another candidate.`,
    };
  }

  const annualSalary = Math.round(found.tier.baseAnnualSalary * country.salaryMultiplier);
  const isSameTrack = state.player.job?.trackId === trackId;

  return {
    state: {
      ...state,
      player: {
        ...state.player,
        retired: false,
        job: {
          trackId,
          tierId,
          title: found.tier.title,
          annualSalary,
          performance: 60,
          monthsInTrack: isSameTrack ? (state.player.job?.monthsInTrack ?? 0) : 0,
          monthsInTier: 0,
          bossNpcId: state.player.job?.bossNpcId ?? null,
        },
        highestJobTitle: found.tier.title,
      },
    },
    success: true,
    message: `Hired as ${found.tier.title} (${country.currencySymbol}${annualSalary.toLocaleString()}/yr)!`,
  };
}

export function requestPromotion(
  state: GameState,
  pack: EraPack,
  rng: SeededRng,
): { state: GameState; success: boolean; message: string } {
  if (!state.player.job) return { state, success: false, message: 'You are not currently employed.' };

  const found = findCareerTier(pack, state.player.job.trackId, state.player.job.tierId);
  if (!found) return { state, success: false, message: 'Current career tier not found.' };

  const nextTier = found.track.tiers.find((t) => t.level === found.tier.level + 1);
  if (!nextTier) {
    // Already at top tier: negotiate a raise
    const raiseSuccess = rng.chance(clamp(state.player.job.performance / 120, 0.25, 0.85));
    if (!raiseSuccess) {
      return { state, success: false, message: 'Board declined a raise this cycle.' };
    }
    const newSalary = Math.round(state.player.job.annualSalary * 1.08);
    return {
      state: {
        ...state,
        player: {
          ...state.player,
          job: { ...state.player.job, annualSalary: newSalary },
        },
      },
      success: true,
      message: `Negotiated an 8% executive raise!`,
    };
  }

  const yearsInTrack = state.player.job.monthsInTrack / 12;
  if (nextTier.minYearsInTrack && yearsInTrack < nextTier.minYearsInTrack) {
    return {
      state,
      success: false,
      message: `Need ${nextTier.minYearsInTrack} years in track for ${nextTier.title} (have ${yearsInTrack.toFixed(1)}y).`,
    };
  }
  for (const [skill, minVal] of Object.entries(nextTier.minSkills)) {
    if ((state.player.skills[skill as SkillId] ?? 0) < (minVal ?? 0)) {
      return { state, success: false, message: `Requires ${skill} ${minVal}+ for ${nextTier.title}.` };
    }
  }

  const country = getCountry(pack, state.config.countryId);
  const promoChance = clamp(
    (state.player.job.performance - 35) / 80 + state.player.stats.charisma * 0.002,
    0.15,
    0.92,
  );
  if (!rng.chance(promoChance)) {
    return {
      state,
      success: false,
      message: `Passed over for ${nextTier.title} this cycle. Keep boosting performance.`,
    };
  }

  const newSalary = Math.round(nextTier.baseAnnualSalary * country.salaryMultiplier);
  return {
    state: {
      ...state,
      player: {
        ...state.player,
        job: {
          ...state.player.job,
          tierId: nextTier.id,
          title: nextTier.title,
          annualSalary: newSalary,
          monthsInTier: 0,
        },
        highestJobTitle: nextTier.title,
      },
    },
    success: true,
    message: `Promoted to ${nextTier.title} (${country.currencySymbol}${newSalary.toLocaleString()}/yr)!`,
  };
}

export interface CareerFinanceTickOutput {
  nextEducation: GameState['player']['education'];
  nextJob: GameState['player']['job'];
  nextFinance: GameState['player']['finance'];
  skillDeltas: Partial<Record<SkillId, number>>;
  stressDelta: number;
  happinessDelta: number;
  logs: Array<{ category: 'career' | 'finance' | 'milestone'; title: string; detail: string }>;
}

export function tickEducationCareerAndFinance(
  state: GameState,
  pack: EraPack,
  rng: SeededRng,
  overtimeHours: number,
  habitAnnualCost: number,
): CareerFinanceTickOutput {
  const dt = getDt(state.config.granularity);
  const monthsToAdd = getMonthsPerTurn(state.config.granularity);
  const ageYears = getAgeYears(state.player.ageMonths);
  const country = getCountry(pack, state.config.countryId);
  const macro = pack.meta.macroStates[state.world.macroState];

  const logs: CareerFinanceTickOutput['logs'] = [];
  const skillDeltas: Partial<Record<SkillId, number>> = {};
  let stressDelta = 0;
  let happinessDelta = 0;

  // 1. Education progression & auto-enrollment for Primary / High School
  const nextEducation = { ...state.player.education, completedProgramIds: [...state.player.education.completedProgramIds] };

  if (!nextEducation.currentProgramId) {
    if (ageYears >= 5 && ageYears < 13 && !nextEducation.completedProgramIds.includes('primary')) {
      nextEducation.currentProgramId = 'primary';
      nextEducation.monthsCompletedInProgram = 0;
      logs.push({ category: 'milestone', title: 'Started Primary School', detail: 'Your school years officially begin.' });
    } else if (
      ageYears >= 13 &&
      ageYears < 18 &&
      nextEducation.completedProgramIds.includes('primary') &&
      !nextEducation.completedProgramIds.includes('high_school')
    ) {
      nextEducation.currentProgramId = 'high_school';
      nextEducation.monthsCompletedInProgram = 0;
      logs.push({ category: 'milestone', title: 'Started High School', detail: 'Welcome to adolescence, exams, and new ambitions.' });
    }
  }

  let tuitionTurnCost = 0;
  if (nextEducation.currentProgramId) {
    const prog = pack.education.find((e) => e.id === nextEducation.currentProgramId);
    if (prog) {
      nextEducation.monthsCompletedInProgram += monthsToAdd;
      tuitionTurnCost = Math.round(prog.annualTuition * country.tuitionMultiplier * dt);

      // GPA drift from intelligence + discipline + overtime study
      const studyFactor = (state.player.stats.intelligence + state.player.stats.discipline) / 200;
      nextEducation.gpa = clamp(
        Number((nextEducation.gpa + (studyFactor - 0.45 + overtimeHours * 0.01) * 0.4 * dt).toFixed(2)),
        0.5,
        4.0,
      );

      for (const skill of prog.skillTags) {
        skillDeltas[skill] = (skillDeltas[skill] ?? 0) + 7 * dt;
      }

      if (nextEducation.monthsCompletedInProgram >= prog.durationYears * 12) {
        nextEducation.completedProgramIds.push(prog.id);
        nextEducation.currentProgramId = null;
        nextEducation.monthsCompletedInProgram = 0;
        happinessDelta += 8;
        logs.push({
          category: 'milestone',
          title: `Graduated: ${prog.name}`,
          detail: `Finished with a ${nextEducation.gpa.toFixed(2)} GPA.`,
        });
      }
    }
  }

  // 2. Job ticking
  let nextJob = state.player.job ? { ...state.player.job } : null;
  let grossAnnualIncome = 0;

  if (nextJob) {
    const found = findCareerTier(pack, nextJob.trackId, nextJob.tierId);
    nextJob.monthsInTrack += monthsToAdd;
    nextJob.monthsInTier += monthsToAdd;

    // Performance evolves from discipline, overtime, and burnout
    const perfDrift =
      (state.player.stats.discipline - 50) * 0.15 +
      overtimeHours * 0.9 -
      (state.player.hidden.burnout > 65 ? 12 : 0);
    nextJob.performance = clamp(nextJob.performance + perfDrift * dt, 10, 100);

    // Annual merit/macro salary growth
    if (nextJob.performance >= 65) {
      nextJob.annualSalary = Math.round(
        nextJob.annualSalary * scaleCompoundFactor(macro.salaryGrowthBonus + 0.01, state.config.granularity),
      );
    }

    grossAnnualIncome += nextJob.annualSalary;

    if (found) {
      stressDelta += found.tier.annualStressImpact * dt;
      for (const [skill, rate] of Object.entries(found.tier.skillGrowth)) {
        skillDeltas[skill as SkillId] = (skillDeltas[skill as SkillId] ?? 0) + (rate ?? 0) * dt;
      }
    }

    // Recession / low-performance layoff risk
    const annualLayoffRisk =
      (nextJob.performance < 30 ? 0.25 : 0.025) * macro.layoffRiskMultiplier;
    if (rng.chance(scaleAnnualProbability(annualLayoffRisk, state.config.granularity))) {
      logs.push({
        category: 'career',
        title: `Laid Off from ${nextJob.title}`,
        detail: `Budget cuts and restructuring cost you your position.`,
      });
      nextJob = null;
      stressDelta += 14;
      happinessDelta -= 12;
    }
  } else if (state.player.retired && ageYears >= country.retirementAge) {
    grossAnnualIncome += country.annualPensionBase;
  }

  // 3. Finance: Taxes, Living Cost, Housing, Student Loans, Investments
  const annualTax = calculateAnnualIncomeTax(grossAnnualIncome, country);
  const turnGrossIncome = Math.round(grossAnnualIncome * dt);
  const turnTax = Math.round(annualTax * dt);
  const turnNetIncome = turnGrossIncome - turnTax;

  const housingDef = pack.assets.housing.find((h) => h.id === state.player.finance.housingId);
  if (housingDef) {
    happinessDelta += housingDef.happinessBonus * 0.35 * dt;
    stressDelta -= housingDef.stressReduction * 0.35 * dt;
  }

  // Minors (<18) have living expenses covered by parents
  let turnExpenses = 0;
  if (ageYears >= 18) {
    const baseLiving = country.baseAnnualLivingCost * state.world.inflationIndex * dt;
    const rent =
      !state.player.finance.ownsHousing && housingDef
        ? housingDef.annualRent * country.costOfLivingMultiplier * state.world.inflationIndex * dt
        : 0;
    const dependentsCount = state.npcs.filter(
      (n) => n.alive && n.role === 'child' && getAgeYears(n.ageMonths) < 18,
    ).length;
    const dependentCost = dependentsCount * 4500 * country.costOfLivingMultiplier * dt;
    const habitCost = habitAnnualCost * country.costOfLivingMultiplier * dt;
    turnExpenses = Math.round(baseLiving + rent + dependentCost + habitCost);
  }

  // Handle tuition (pay from cash if affordable, else add to student loans)
  let cash = state.player.finance.cash + turnNetIncome - turnExpenses;
  if (tuitionTurnCost > 0) {
    if (cash >= tuitionTurnCost) {
      cash -= tuitionTurnCost;
      turnExpenses += tuitionTurnCost;
    } else {
      nextEducation.studentLoanBalance += tuitionTurnCost;
    }
  }

  // Student loan interest & minimum repayment once graduated & employed
  if (nextEducation.studentLoanBalance > 0) {
    nextEducation.studentLoanBalance = Math.round(
      nextEducation.studentLoanBalance * scaleCompoundFactor(0.055, state.config.granularity),
    );
    if (!nextEducation.currentProgramId && grossAnnualIncome > 0) {
      const payment = Math.min(nextEducation.studentLoanBalance, Math.round(turnNetIncome * 0.12));
      if (payment > 0 && cash >= payment) {
        cash -= payment;
        nextEducation.studentLoanBalance -= payment;
        turnExpenses += payment;
      }
    }
  }

  // Mortgage interest & payment
  let mortgageBalance = state.player.finance.mortgageBalance;
  if (mortgageBalance > 0) {
    mortgageBalance = Math.round(mortgageBalance * scaleCompoundFactor(0.06, state.config.granularity));
    const payment = Math.min(mortgageBalance, Math.round(mortgageBalance * 0.08 * dt + 1200 * dt));
    cash -= payment;
    mortgageBalance -= payment;
    turnExpenses += payment;
  }

  // Investment market returns (macro baseline + asset expected return + Gaussian volatility scaled by sqrt(dt))
  const volatilityScale = (0.5 + state.config.economyVolatility / 100) * Math.sqrt(dt);
  const nextInvestments: InvestmentHolding[] = state.player.finance.investments.map((holding) => {
    const asset = pack.assets.investments.find((a) => a.id === holding.assetId);
    if (!asset) return holding;
    const blendedAnnual = (asset.expectedAnnualReturn + macro.annualMarketReturn) / 2;
    const randomShock = rng.nextGaussian() * asset.annualVolatility * volatilityScale;
    const turnReturn = blendedAnnual * dt + randomShock;
    const nextBalance = Math.max(0, Math.round(holding.balance * (1 + turnReturn)));
    return { ...holding, balance: nextBalance };
  });

  // Credit score update
  let creditScore = state.player.finance.creditScore;
  if (ageYears >= 18) {
    if (cash < 0) {
      creditScore -= Math.round(18 * dt);
    } else if (turnNetIncome > turnExpenses) {
      creditScore += Math.round(8 * dt);
    }
    creditScore = clamp(creditScore, 300, 850);
  }

  const nextFinance: GameState['player']['finance'] = {
    ...state.player.finance,
    cash: Math.round(cash),
    creditScore,
    mortgageBalance,
    investments: nextInvestments,
    lastTurnIncome: turnNetIncome,
    lastTurnExpenses: turnExpenses,
    lastTurnTax: turnTax,
    netWorthHistory: state.player.finance.netWorthHistory,
  };

  return {
    nextEducation,
    nextJob,
    nextFinance,
    skillDeltas,
    stressDelta,
    happinessDelta,
    logs,
  };
}

export function investInAsset(
  state: GameState,
  assetId: string,
  amount: number,
  pack: EraPack,
): { state: GameState; success: boolean; message: string } {
  const asset = pack.assets.investments.find((a) => a.id === assetId);
  if (!asset) return { state, success: false, message: 'Investment asset not found.' };
  if (amount < asset.minInvestment) {
    return { state, success: false, message: `Minimum investment is ${asset.minInvestment}.` };
  }
  if (state.player.finance.cash < amount) {
    return { state, success: false, message: 'Insufficient cash.' };
  }

  const existing = state.player.finance.investments.find((h) => h.assetId === assetId);
  const nextInvestments = existing
    ? state.player.finance.investments.map((h) =>
        h.assetId === assetId
          ? { ...h, balance: h.balance + amount, costBasis: h.costBasis + amount }
          : h,
      )
    : [...state.player.finance.investments, { assetId, balance: amount, costBasis: amount }];

  return {
    state: {
      ...state,
      player: {
        ...state.player,
        finance: {
          ...state.player.finance,
          cash: state.player.finance.cash - amount,
          investments: nextInvestments,
        },
      },
    },
    success: true,
    message: `Invested ${amount} in ${asset.name}.`,
  };
}

export function sellInvestment(
  state: GameState,
  assetId: string,
  amount: number,
): { state: GameState; success: boolean; message: string } {
  const holding = state.player.finance.investments.find((h) => h.assetId === assetId);
  if (!holding || holding.balance <= 0) {
    return { state, success: false, message: 'No holdings in this asset.' };
  }
  const sellAmt = Math.min(amount, holding.balance);
  const remaining = holding.balance - sellAmt;
  const nextInvestments =
    remaining <= 0
      ? state.player.finance.investments.filter((h) => h.assetId !== assetId)
      : state.player.finance.investments.map((h) =>
          h.assetId === assetId
            ? { ...h, balance: remaining, costBasis: Math.round(h.costBasis * (remaining / holding.balance)) }
            : h,
        );

  return {
    state: {
      ...state,
      player: {
        ...state.player,
        finance: {
          ...state.player.finance,
          cash: state.player.finance.cash + sellAmt,
          investments: nextInvestments,
        },
      },
    },
    success: true,
    message: `Sold ${sellAmt} of holdings.`,
  };
}
