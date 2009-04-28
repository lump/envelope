--
-- $Id: bootstrap-psql.sql,v 1.2 2009/04/28 22:48:12 troy Exp $
--

-- this script creates the budget role, schema, database, and tables.
-- run it like this:
-- $ psql -U postgres < bootstrap-psql.sql

drop database if exists envelope;
drop role if exists budget;

create role budget with nosuperuser login unencrypted password 'tegdub';
alter role budget set search_path to '$user', public;
create database envelope with owner=budget encoding='UTF8';
\connect envelope
create schema authorization budget;
grant all on schema budget to budget;
\connect envelope budget;

create table budgets (
  id serial unique primary key not null,
  stamp timestamp default now() not null,
  name varchar(64) not null
);
insert into budgets (id, name) values(0, 'Test Budget');

create type account_type as enum ('Debit','Credit','Loan');
create table accounts (
  id serial unique primary key not null,
  stamp timestamp default now() not null,
  budget int not null,
  name varchar(64) not null,
  type account_type not null default 'Debit',
  rate numeric not null default 0.0,
  "limit" numeric not null default 0.0,
  constraint name_type unique (name, type),
  constraint accounts_budget foreign key (budget) references budgets(id) ON UPDATE CASCADE ON DELETE RESTRICT
);
insert into accounts values (0, now(), 0, 'Guest''s Checking','Debit', '0.0', '0.0');

create type allocation_settings_type as
enum ('Reimbursement','Weekly_Payday','Biweekly_Payday','Semimonthly_Payday','Monthly_Payday');

create table allocation_settings (
  id serial unique primary key not null,
  stamp timestamp default now() not null,
  budget int not null,
  name varchar(64) not null,
  type allocation_settings_type not null,
  reference_date date not null,
  constraint budget_name unique (budget,name),
  constraint allocation_settings_budget foreign key (budget) references budgets(id) ON UPDATE CASCADE ON DELETE RESTRICT
);
insert into allocation_settings values(0,now(),0,'Guest''s Main Job','Semimonthly_Payday',now());

create table categories (
  id serial unique primary key not null,
  stamp timestamp default now() not null,
  account int not null,
  name varchar(64) NOT NULL default '',
  constraint account_name unique (account,name),
  constraint categories_accounts foreign key (account) references accounts(id) ON UPDATE CASCADE ON DELETE RESTRICT
);

create type category_allocation_settings_type as enum('ppp','fpp','fpm');
create table category_allocation_settings (
  id serial unique primary key not null,
  stamp timestamp default now() not null,
  allocation_setting int not null,
  category int not null,
  allocation numeric not null default '0.0',
  allocation_type category_allocation_settings_type not null default 'ppp',
  auto_deduct boolean not null default false,
  constraint cat_alloc_settings_categories foreign key (category) references categories(id) on update cascade on delete restrict,
  constraint cat_alloc_settings_alloc_settings foreign key (allocation_setting) references allocation_settings(id) on update cascade on delete restrict
);

insert into categories values(0, now(), 0, 'Tithing');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, 0, '.1','ppp', false);
insert into categories (account, name) values(0, 'Water');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),22.5,'fpm',false);
insert into categories (account, name) values(0, 'Subscriptions');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),2,'fpp',false);
insert into categories (account, name) values(0, 'Travel');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),0,'ppp',false);
insert into categories (account, name) values(0, 'State Tax');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),3.76,'ppp',true);
insert into categories (account, name) values(0, 'Social Security');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),90,'fpp',true);
insert into categories (account, name) values(0, 'Sewer');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),2.5,'fpm',false);
insert into categories (account, name) values(0, 'Restaurants');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),10,'fpp',false);
insert into categories (account, name) values(0, 'Recreation');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),5,'fpp',false);
insert into categories (account, name) values(0, 'Phone');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),30,'fpm',false);
insert into categories (account, name) values(0, 'Other Taxes and Fees');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),0,'ppp',false);
insert into categories (account, name) values(0, 'Mortgage');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),600,'fpm',false);
insert into categories (account, name) values(0, 'Memberships');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),0,'ppp',false);
insert into categories (account, name) values(0, 'Medicare');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),30,'fpp',true);
insert into categories (account, name) values(0, 'Medical Insurance');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),100,'fpp',true);
insert into categories (account, name) values(0, 'Medical');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),46,'fpm',false);
insert into categories (account, name) values(0, 'Media');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),0,'fpp',false);
insert into categories (account, name) values(0, 'Home');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),40,'fpm',false);
insert into categories (account, name) values(0, 'Hobbies');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),0,'ppp',false);
insert into categories (account, name) values(0, 'Haircuts');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),4,'fpm',false);
insert into categories (account, name) values(0, 'Groceries');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),200,'fpp',false);
insert into categories (account, name) values(0, 'Gifts');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),20,'fpp',false);
insert into categories (account, name) values(0, 'Gasoline');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),30,'fpp',false);
insert into categories (account, name) values(0, 'Gas');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),50,'fpm',false);
insert into categories (account, name) values(0, 'Furniture');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),7,'ppp',false);
insert into categories (account, name) values(0, 'Federal Tax');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),0,'ppp',true);
insert into categories (account, name) values(0, 'Fast Offering');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),0,'ppp',false);
insert into categories (account, name) values(0, 'Electronics');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),0,'ppp',false);
insert into categories (account, name) values(0, 'Computer');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),15,'fpm',false);
insert into categories (account, name) values(0, 'Clothes');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),15,'fpp',false);
insert into categories (account, name) values(0, 'Car Payment');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),6.25,'ppp',false);
insert into categories (account, name) values(0, 'Car Maintenance');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),12,'fpm',false);
insert into categories (account, name) values(0, 'Car Insurance');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),266.68,'fpm',false);
insert into categories (account, name) values(0, 'Electricity');
insert into category_allocation_settings (allocation_setting, category, allocation, allocation_type, auto_deduct) values (0, (currval('categories_id_seq')),70,'fpm',false);

create table tags (
  id serial unique primary key not null,
  stamp timestamp default now() not null,
  budget int not null,
  name varchar(64) not null,
  constraint tags_budget foreign key (budget) references budgets(id) ON UPDATE CASCADE ON DELETE RESTRICT
);
insert into tags values (0, now(), 0, 'Adjustment');

create table transactions (
  id serial unique primary key not null,
  stamp timestamp default now() not null,
  date date default now() not null,
  entity varchar(128) not null,
  description varchar(255) not null,
  reconciled boolean not null default false,
  transfer boolean not null default false
);
insert into transactions values (0, now(), now(), 'Beginning Balance', 'Starting Balance');
insert into transactions (entity, description) values ('Beginning Balance', 'Starting Balance');

create table allocation_tag (
  allocation int NOT NULL,
  tag int NOT NULL
);
insert into allocation_tag values (0, 0);
insert into allocation_tag values (1, 0);

create table allocations (
  id serial unique primary key not null,
  stamp timestamp default now() not null,
  category int not null,
  transaction int not null,
  amount numeric(8,2) not null,
  constraint category foreign key (category) references categories(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  constraint transaction foreign key (transaction) references transactions(id) ON UPDATE CASCADE ON DELETE RESTRICT
);
insert into allocations (category, transaction, amount) values (0, 0, '5.3');
insert into allocations (category, transaction, amount) values (0, 0, '3.2');
insert into allocations (category, transaction, amount) values (0, 1, '2.2');
insert into allocations (category, transaction, amount) values (0, 1, '4.4');
insert into allocations (category, transaction, amount) values (0, 1, '6.6');

create table users (
  id serial unique primary key not null,
  stamp timestamp default now() not null,
  budget int NOT NULL,
  name varchar(64) unique not null,
  real_name varchar(64) default NULL,
  crypt_password varchar(34) default NULL,
  permissions int default NULL,
  public_key bytea,
  constraint users_budget foreign key (budget) references budgets(id) on update cascade on delete restrict
);
insert into users (budget, name, real_name, crypt_password, permissions)
values (0,'admin','Admin Account','$1$INZEtT3S$D81Kp34n4Oea5Rs97lPOq0',7);

insert into users (budget, name, real_name, crypt_password, permissions)
values (0, 'guest','Guest Account','$1$GOyqcoAk$KTE1zfxeTkoXJTcrFKyFi0',3);
