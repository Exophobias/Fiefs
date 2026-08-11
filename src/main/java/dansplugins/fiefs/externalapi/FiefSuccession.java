package dansplugins.fiefs.externalapi;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a {@link FiefSuccessionPolicy} answers: who takes a fief, under what rule, and in words a
 * player reads.
 *
 * <p>Every string here is written by the rule that produced it and never by Fiefs, which is the same
 * discipline Medieval Factions' {@code Succession.explanation} sets at realm scale. Fiefs holds no
 * concept of a government form and must never compose a sentence that names one: a message assembled
 * by the caller drifts out of step with the rule the moment the rule changes, and the player is then
 * told something that was true last month.
 *
 * <p><b>Nothing here is authority.</b> A record is an answer to a question, not a seat. Fiefs
 * validates {@link #successor()} against the eligible list before using it, discards the whole answer
 * if it fails, and falls back to its own ladder. See {@link FiefSuccessionPolicy} for the contract.
 *
 * @param successor         who inherits (from {@link FiefSuccessionPolicy#decide}) or who would
 *                          inherit today (from {@link FiefSuccessionPolicy#standingFor}). <b>Null
 *                          from {@code standingFor}</b> means nobody is eligible and the fief would
 *                          revert to the faction that granted it. Null from {@code decide} is not a
 *                          usable answer: reverting is Fiefs' own call and no policy can reach it in
 *                          either direction, so a null successor there is discarded as a deferral.
 * @param rule              a short label for a readout header, for example
 *                          {@code "Council, as Ashford is governed"} or {@code "the ordinary line"}.
 *                          Printed after {@code Rule:} on {@code /fi succession}, so it is a noun
 *                          phrase rather than a sentence and carries no full stop.
 * @param explanation       the sentence Fiefs prints under the announcement, <b>past tense from
 *                          {@code decide}</b> ("its elders had chosen them") and <b>present tense
 *                          from {@code standingFor}</b> ("its elders have chosen them"), because one
 *                          describes something that happened and the other something standing. A
 *                          missing or blank one on an otherwise valid {@code decide} answer does NOT
 *                          discard the seat: it is replaced with a generic sentence and logged once
 *                          at WARNING. Losing a player their fief over a null string is the wrong
 *                          direction.
 * @param holderMayNameHeir false under any form where the holder's own nomination is not the rule.
 *                          This is what lets {@code /fi heir} refuse correctly without Fiefs ever
 *                          naming a government type, and it is read from {@code standingFor} only.
 * @param heirRefusal       the sentence {@code /fi heir} prints when {@link #holderMayNameHeir()} is
 *                          false. Must be non-null exactly then. A missing one is replaced with a
 *                          generic refusal and logged once; a missing string never changes behaviour,
 *                          so the command still refuses.
 */
public record FiefSuccession(@Nullable UUID successor,
                             String rule,
                             String explanation,
                             boolean holderMayNameHeir,
                             @Nullable String heirRefusal) {
}
