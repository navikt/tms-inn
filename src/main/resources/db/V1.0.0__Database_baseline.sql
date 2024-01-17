create table alert_header(
    referenceId text primary key,
    tekster jsonb not null,
    opprettet timestamp with time zone not null,
    opprettetAv jsonb not null,
    aktiv boolean not null default true,
    avsluttet timestamp with time zone
);

create table aktiv_alert_regel(
    alert_ref text primary key references alert_header(referenceId),
    domener jsonb,
    brukere jsonb,
    aktivTil timestamp with time zone,
    opprettet timestamp with time zone
);

create table alert_varsel_queue(
    alert_ref text references alert_header(referenceId),
    ident text not null,
    sendt boolean not null default false,
    opprettet timestamp with time zone not null,
    ferdigstilt timestamp with time zone,
    constraint unique (alert_ref, ident)
);

create index aktiv_alert_regel_ref on aktiv_alert_regel(alert_ref);
create index aktiv_alert_regel_ident on aktiv_alert_regel using gin (brukere);
create index alert_varsel_queue_ref on alert_varsel_queue(alert_ref);
create index alert_varsel_queue_ident on alert_varsel_queue(ident);
