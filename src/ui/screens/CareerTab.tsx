import React, { useState } from 'react';
import { Award, Briefcase, GraduationCap, LogOut, TrendingUp } from 'lucide-react';
import {
  canApplyForJob,
  canEnrollInProgram,
  getAgeYears,
  getCountry,
} from '../../engine';
import type { EraPack, GameState, PlayerAction, SkillId } from '../../engine';
import { formatMoney } from '../../store/saveStore';

interface CareerTabProps {
  state: GameState;
  pack: EraPack;
  onAction: (action: PlayerAction) => void;
  onChangeFocusSkill: (skill: SkillId) => void;
}

export const CareerTab: React.FC<CareerTabProps> = ({
  state,
  pack,
  onAction,
  onChangeFocusSkill,
}) => {
  const [subTab, setSubTab] = useState<'jobs' | 'education' | 'skills'>('jobs');
  const country = getCountry(pack, state.config.countryId);
  const ageYears = getAgeYears(state.player.ageMonths);
  const { job, education, skills, focusSkill } = state.player;

  const currentProgram = education.currentProgramId
    ? pack.education.find((e) => e.id === education.currentProgramId)
    : null;

  const canRetire = !state.player.retired && ageYears >= country.retirementAge - 5;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {/* Sub-tabs */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 6 }}>
        <button
          className={`btn btn-sm ${subTab === 'jobs' ? 'btn-primary' : ''}`}
          onClick={() => setSubTab('jobs')}
        >
          <Briefcase size={13} /> Career
        </button>
        <button
          className={`btn btn-sm ${subTab === 'education' ? 'btn-sky' : ''}`}
          onClick={() => setSubTab('education')}
        >
          <GraduationCap size={13} /> Education
        </button>
        <button
          className={`btn btn-sm ${subTab === 'skills' ? 'btn-violet' : ''}`}
          onClick={() => setSubTab('skills')}
        >
          <Award size={13} /> Skills
        </button>
      </div>

      {/* CAREER SUBTAB */}
      {subTab === 'jobs' && (
        <>
          {/* Current Job */}
          <div className="card">
            <div className="section-header">
              <span className="section-title">Current Employment</span>
              {state.player.retired && <span className="chip chip-amber">Retired</span>}
            </div>

            {job ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <h3 style={{ fontSize: 16, fontWeight: 800 }}>{job.title}</h3>
                    <div className="tabular" style={{ fontSize: 12, color: 'var(--accent-emerald)', fontWeight: 700, marginTop: 2 }}>
                      {formatMoney(job.annualSalary, country)} / yr
                    </div>
                  </div>
                  <span className="chip tabular">
                    {Math.floor(job.monthsInTrack / 12)}y {job.monthsInTrack % 12}m
                  </span>
                </div>

                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11.5, marginBottom: 4 }}>
                    <span style={{ color: 'var(--text-secondary)' }}>Performance Rating</span>
                    <strong className="tabular">{Math.round(job.performance)}%</strong>
                  </div>
                  <div className="bar-track">
                    <div
                      className="bar-fill"
                      style={{ width: `${Math.round(job.performance)}%`, backgroundColor: 'var(--accent-emerald)' }}
                    />
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 6 }}>
                  <button
                    className="btn btn-sm btn-primary"
                    style={{ flex: 1 }}
                    onClick={() => onAction({ type: 'REQUEST_PROMOTION' })}
                  >
                    <TrendingUp size={13} /> Ask Promotion (1 AP)
                  </button>
                  <button
                    className="btn btn-sm btn-rose"
                    onClick={() => onAction({ type: 'QUIT_JOB' })}
                  >
                    <LogOut size={13} /> Quit
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
                {ageYears < 14
                  ? 'Too young to work yet. Focus on school and early skills!'
                  : state.player.retired
                    ? 'Enjoying retirement and pension income.'
                    : 'Currently unemployed. Browse career ladders below to apply.'}
              </div>
            )}

            {canRetire && (
              <button
                className="btn btn-sm btn-amber"
                style={{ width: '100%', marginTop: 10 }}
                onClick={() => onAction({ type: 'RETIRE' })}
              >
                Officially Retire (Starts Pension at {country.retirementAge}y)
              </button>
            )}
          </div>

          {/* Job Board */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {pack.careers.map((track) => (
              <div key={track.id} className="card">
                <div className="section-header" style={{ marginBottom: 8 }}>
                  <span style={{ fontSize: 13.5, fontWeight: 700 }}>{track.name}</span>
                  <span className="chip">{track.isPartTime ? 'Part-Time' : track.industry}</span>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {track.tiers.map((tier) => {
                    const check = canApplyForJob(state, track, tier);
                    const isCurrent = job?.trackId === track.id && job?.tierId === tier.id;
                    const estSalary = Math.round(tier.baseAnnualSalary * country.salaryMultiplier);

                    return (
                      <div
                        key={tier.id}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          gap: 8,
                          padding: '8px 10px',
                          borderRadius: 9,
                          backgroundColor: 'var(--bg-input)',
                          border: `1px solid ${isCurrent ? 'var(--accent-emerald)' : 'var(--border-subtle)'}`,
                        }}
                      >
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: 12.5, fontWeight: 600 }}>{tier.title}</div>
                          <div className="tabular" style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                            ~{formatMoney(estSalary, country)}/yr • {tier.weeklyHoursRequired}h/wk
                          </div>
                          {!check.eligible && !isCurrent && (
                            <div style={{ fontSize: 10.5, color: 'var(--text-muted)', marginTop: 2 }}>
                              {check.reason}
                            </div>
                          )}
                        </div>

                        {isCurrent ? (
                          <span className="chip chip-emerald">Current</span>
                        ) : (
                          <button
                            className="btn btn-sm btn-primary"
                            disabled={!check.eligible}
                            onClick={() =>
                              onAction({ type: 'APPLY_JOB', trackId: track.id, tierId: tier.id })
                            }
                            style={{ flexShrink: 0 }}
                          >
                            Apply
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {/* EDUCATION SUBTAB */}
      {subTab === 'education' && (
        <>
          <div className="card">
            <div className="section-header">
              <span className="section-title">Current Enrollment</span>
              <span className="chip chip-sky tabular">GPA {education.gpa.toFixed(2)}</span>
            </div>

            {currentProgram ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <div style={{ fontSize: 15, fontWeight: 700 }}>{currentProgram.name}</div>
                <div className="tabular" style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                  Progress: {education.monthsCompletedInProgram} / {currentProgram.durationYears * 12} months • Study floor: {currentProgram.weeklyStudyHoursRequired}h/wk
                </div>
                <div className="bar-track">
                  <div
                    className="bar-fill"
                    style={{
                      width: `${Math.min(100, Math.round((education.monthsCompletedInProgram / (currentProgram.durationYears * 12)) * 100))}%`,
                      backgroundColor: 'var(--accent-sky)',
                    }}
                  />
                </div>
              </div>
            ) : (
              <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
                Not currently enrolled. Primary & High School auto-enroll at ages 5 and 13, or apply to higher education below.
              </div>
            )}

            {education.studentLoanBalance > 0 && (
              <div className="tabular" style={{ marginTop: 8, fontSize: 12, color: 'var(--accent-rose)' }}>
                Student Loan Debt: {formatMoney(education.studentLoanBalance, country)}
              </div>
            )}
          </div>

          <div className="card">
            <div className="section-header">
              <span className="section-title">Programs & Degrees</span>
              <span className="chip">{education.completedProgramIds.length} Completed</span>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
              {pack.education.map((prog) => {
                const completed = education.completedProgramIds.includes(prog.id);
                const enrolled = education.currentProgramId === prog.id;
                const check = canEnrollInProgram(state, prog);
                const annualTuition = Math.round(prog.annualTuition * country.tuitionMultiplier);

                return (
                  <div
                    key={prog.id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 8,
                      padding: '8px 10px',
                      borderRadius: 9,
                      backgroundColor: 'var(--bg-input)',
                      border: `1px solid ${completed ? 'rgba(16,185,129,0.4)' : 'var(--border-subtle)'}`,
                    }}
                  >
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontSize: 12.5, fontWeight: 600 }}>{prog.name}</div>
                      <div className="tabular" style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                        {prog.durationYears}y • {annualTuition === 0 ? 'Free' : `${formatMoney(annualTuition, country)}/yr`}
                      </div>
                      {!completed && !enrolled && !check.eligible && (
                        <div style={{ fontSize: 10.5, color: 'var(--text-muted)', marginTop: 2 }}>
                          {check.reason}
                        </div>
                      )}
                    </div>

                    {completed ? (
                      <span className="chip chip-emerald">Graduated</span>
                    ) : enrolled ? (
                      <span className="chip chip-sky">Enrolled</span>
                    ) : (
                      <button
                        className="btn btn-sm btn-sky"
                        disabled={!check.eligible}
                        onClick={() => onAction({ type: 'ENROLL_EDUCATION', programId: prog.id })}
                        style={{ flexShrink: 0 }}
                      >
                        Enroll (1 AP)
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </>
      )}

      {/* SKILLS SUBTAB */}
      {subTab === 'skills' && (
        <div className="card">
          <div className="section-header">
            <span className="section-title">Skills & Mastery</span>
            <span className="chip chip-sky">Tap to Focus</span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {(Object.keys(skills) as SkillId[]).map((sk) => {
              const val = Math.round(skills[sk] ?? 0);
              const isFocus = focusSkill === sk;
              return (
                <button
                  key={sk}
                  onClick={() => onChangeFocusSkill(sk)}
                  style={{
                    width: '100%',
                    textAlign: 'left',
                    padding: '8px 10px',
                    borderRadius: 9,
                    backgroundColor: isFocus ? 'var(--accent-sky-soft)' : 'var(--bg-input)',
                    border: `1px solid ${isFocus ? 'var(--accent-sky)' : 'var(--border-subtle)'}`,
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, marginBottom: 4 }}>
                    <span style={{ fontWeight: 600, textTransform: 'capitalize' }}>
                      {sk.replace('_', ' ')} {isFocus && '★'}
                    </span>
                    <strong className="tabular">{val} / 100</strong>
                  </div>
                  <div className="bar-track">
                    <div className="bar-fill" style={{ width: `${val}%`, backgroundColor: 'var(--accent-sky)' }} />
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};
