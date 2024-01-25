drop table aktiv_alert_regel;

alter table alert_header add column aktivFremTil timestamp with time zone;

create table web_alert_mottakere(
    alert_ref text primary key references alert_header(referenceId),
    mottakere jsonb not null,
    opprettet timestamp with time zone not null
);

create index web_alert_mottakere_ref on web_alert_mottakere(alert_ref);
create index web_alert_mottakere_ident on web_alert_mottakere using gin (mottakere);
