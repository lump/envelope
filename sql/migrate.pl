#!/usr/bin/perl

# $Id: migrate.pl,v 1.1 2007/07/30 04:25:06 troy Exp $
#
# migrate troy's existing live envelope database
# requires a fresh database (boostrap.sql)

use DBI;

$|=1;

$dbs = {
  source => {
    database => "budgets",
    host => "dublan.net",
    port => 3306,
    user => "budget",
    password => "DeADFeeDBeeF",
  },
  dest => {
    database => "envelope",
    host => "lump.us",
    port => 3306,
    user => "budget",
    password => "1qaz2wsx",
  },
};

for my $db (keys %$dbs) {
    my $this = $dbs->{$db};
    my $dsn = "DBI:mysql:database=" . $this->{database} . ";"
      . "host=" . $this->{host} . ";"
      . "port=" . $this->{port};
    $this->{connection} = DBI->connect($dsn, $this->{user}, $this->{password}) or die $!;
    ${db} = $this->{connection};
}

# dump the entire source database into hashes
# for my $table (qw(categories settings transactions users)) {
#   $dbs->{source}->{$table} = [];
#   $sth = $dbs->{source}->{connection}->prepare("select * from $table") or die $source->errstr;
#   $sth->execute or die $sth->errstr;
#   while (my $row = $sth->fetchrow_hashref()) {
#     push @{$dbs->{source}->{$table}}, $row;
#   }
#   $sth->finish;
# }


$dbs->{dest}->{connection}->do("replace into budgets values (null, null, 'Bowman')") 
  or die $dbs->{source}->{connection}->errstr;
($budget_id) = $dbs->{dest}->{connection}->selectrow_array("select id from budgets where id is null")
  or die $dbs->{source}->{connection}->errstr;
print "Budget id: $budget_id\n";

$accounts = {
    Checking => { id => undef, type => 'Debit', rate => 0.005 },
    Savings => { id => undef, type => 'Debit', rate => 0.0141 },
    MMSavings => { id => undef, type => 'Debit', rate => 0.0140 },
    Odyssey => { id => undef, type => 'Loan', rate => 0.046 },
    Civic => { id => undef, type => 'Loan', rate => 0.0625 },
};

for my $account (qw(Checking Savings MMSavings Odyssey Civic)) {
  my $entry = $accounts->{$account};
  $dbs->{dest}->{connection}->do("replace into accounts values (null, null, ?, ?, ?, ?, 0)", 
                                  undef, $budget_id, $account, $entry->{type}, $entry->{rate});
  ($entry->{id}) = $dbs->{dest}->{connection}->selectrow_array("select id from accounts where id is null")
    or die $dbs->{source}->{connection}->errstr;
  print "$account id: $entry->{id}\n";
}

$dsth = $dbs->{dest}->{connection}->prepare("
insert into users (budget,name,real_name,crypt_password,permissions)
values (?, ?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;

$sth = $dbs->{source}->{connection}->prepare("select *,permissions|0 as int_permissions from users where budgetname = 'bowman'")
  or die $dbs->{source}->{connection}->errstr;;
$sth->execute or die $sth->errstr;
while (my $row = $sth->fetchrow_hashref()) {
  $dsth->execute($budget_id, $row->{username}, $row->{real_name}, $row->{crypt_password}, $row->{int_permissions})
    or die $dsth->errstr;
  print "Inserted $budget_id, $row->{username}, $row->{real_name}, $row->{crypt_password}, $row->{int_permissions}\n";
}
$sth->finish;
$dsth->finish;


$dsth = $dbs->{dest}->{connection}->prepare("
insert into categories (account, name, allocation, allocation_type, auto_deduct)
values (?, ?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;

$sth = $dbs->{source}->{connection}->prepare("select * from categories where budgetname = 'bowman'") or die $dbs->{source}->{connection}->errstr;;
$sth->execute or die $sth->errstr;
while (my $row = $sth->fetchrow_hashref()) {
  my $allocation_amount = $row->{which} eq "fpp"
    ? $row->{fixed_per_paycheck}
    : $row->{which} eq "ppp"
      ? $row->{percent_per_paycheck}
      : $row->{fixed_per_month};

  my $account_id = $accounts->{Checking}->{id};
  if ($row->{category} eq "Savings") { $account_id = $accounts->{Savings}->{id} }
  elsif ($row->{category} eq "Tithing") { $account_id = $accounts->{MMSavings}->{id}; $row->{deducted} = 0 }
  $dsth->execute($account_id, $row->{category}, $allocation_amount, $row->{which}, $row->{deducted})
    or die $dsth->errstr;
  print "inserted $account_id, $row->{category}, $allocation_amount, $row->{which}, $row->{deducted}\n";
}
$sth->finish;
$dsth->finish;

my $categories = {};
$sth = $dbs->{dest}->{connection}->prepare("select * from categories where account = ?") or die $dbs->{source}->{connection}->errstr;;
for my $account (keys %$accounts) {
  $sth->execute($accounts->{$account}->{id}) or die $sth->errstr;
  while (my $row = $sth->fetchrow_hashref()) {
    $categories->{$row->{name}} = $row;
  } 
}
$sth->finish;

$dsth = $dbs->{dest}->{connection}->prepare("
insert into incomes (budget,name,type,reference_date)
values (?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;
$dsth->execute($budget_id, "SOS", 'Biweekly_Payday', '2006-11-30');
$dsth->finish;
print "inserted $budget_id, SOS, Biweekly_Payday, 2006-11-30\n";


$dtrans = $dbs->{dest}->{connection}->prepare("
insert into transactions (date, subcategory, who, description, reconciled)
values (?, ?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;

$dalloc = $dbs->{dest}->{connection}->prepare("
insert into allocations (category, transaction, amount)
values (?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;

#DBI->trace(2);
$sth = $dbs->{source}->{connection}->prepare("select * from transactions where budgetname = 'bowman' order by date, to_from, subcategory") or die $dbs->{source}->{connection}->errstr;;
$sth->execute or die $sth->errstr;
my $last = {};
my $last_id = undef;
print "Inserting transactions(-) and allocations(+)";
while (my $row = $sth->fetchrow_hashref()) {
  if ($row->{to_from} eq undef) { $row->{to_from} = "" }
  if ($row->{description} eq undef) { $row->{description} = "" }
  unless ("Payday" eq $row->{subcategory}
      && "Payday" eq $last->{subcategory}
      && ($row->{to_from} =~ /^SOS|ArosNet$/ && ($row->{to_from} eq $last->{to_from}))
      && ($row->{date} eq $last->{date})
      && $last_id ne undef) {
    my @params = ($row->{date}, $row->{subcategory}, $row->{to_from}, $row->{description}, $row->{reconciled});
    unless ($dtrans->execute(@params)) {
      print "transaction: " . (join ",", @params) . "\n";
      die $dtrans->errstr;
    }
    print "-";
    ($last_id) = $dbs->{dest}->{connection}->selectrow_array("select id from transactions where id is null");
  }

  my @params = ($categories->{$row->{category}}->{id}, $last_id, $row->{amount});
  unless ($dalloc->execute(@params)) {
    print "allocation: " . (join ",", @params) . "\n";
    die $dalloc->errstr;
  }
  print "+";
  $last = $row;
}
print "\n";
$sth->finish;
$dtrans->finish;
$dalloc->finish;

