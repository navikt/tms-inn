alter table alert_varsel_queue rename to alert_beskjed_queue;

alter table alert_beskjed_queue
    add column status text not null default 'venter';

alter table alert_beskjed_queue
    add column feilkilde jsonb;

alter table alert_beskjed_queue rename column sendt to behandlet;

update alert_beskjed_queue set status = 'sendt' where behandlet;
