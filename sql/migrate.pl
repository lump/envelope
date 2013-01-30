#!/usr/bin/perl

# $Id: migrate.pl,v 1.24 2010/12/23 19:19:02 troy Exp $
#
# migrate troy's existing live envelope database
# requires a fresh database (boostrap.sql)

use DBI;

$|=1;

$dbs = {
  source => {
    database => "budgets",
    port => 3306,
    user => "budget",
    host => "dublan.net",
    password => "DeADFeeDBeeF",
  },
  dest => {
    database => "envelope",
    port => 3306,
    host => "localhost",
    user => "budget",
    password => "tegdub",
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
($budget_id) = $dbs->{dest}->{connection}->selectrow_array("select id from budgets where id = last_insert_id()")
  or die $dbs->{source}->{connection}->errstr;
print "Budget id: $budget_id\n";

$accounts = {
    Checking => { id => undef, type => 'Debit', rate => 0.005 },
    Savings => { id => undef, type => 'Debit', rate => 0.0141 },
#    DSavings => { id => undef, type => 'Debit', rate => 0.0475 },
#    Tacoma => { id => undef, type => 'Loan', rate => 0.0 },
};

#for my $account (qw(Checking Savings DSavings Tacoma)) {
for my $account (qw(Checking Savings)) {
  my $entry = $accounts->{$account};
  $dbs->{dest}->{connection}->do("replace into accounts values (null, null, ?, ?, ?, ?, 0)",
                                  undef, $budget_id, $account, $entry->{type}, $entry->{rate});
  ($entry->{id}) = $dbs->{dest}->{connection}->selectrow_array("select id from accounts where id = last_insert_id()")
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
  print "Inserted user $budget_id, $row->{username}, $row->{real_name}, $row->{crypt_password}, $row->{int_permissions}\n";
}

# add a test user for this budget
$dsth->execute($budget_id, 'bowmantest', 'Bowman Test Account','$1$GOyqcoAk$KTE1zfxeTkoXJTcrFKyFi0',7);
$sth->finish;
$dsth->finish;


#$dsth = $dbs->{dest}->{connection}->prepare(
#  "insert into allocation_settings (budget,name,type,reference_date) values (?, ?, ?, ?)")
#  or die $dbs->{source}->{connection}->errstr;;
#$dsth->execute($budget_id, "SOS", 'Biweekly_Payday', '2006-11-30');
#$dsth->finish;
#print "inserted allocation setting $budget_id, SOS, Biweekly_Payday, 2006-11-30\n";
#($allocation_setting_id) = $dbs->{dest}->{connection}->selectrow_array("select id from allocation_settings where id is null");

$dsth = $dbs->{dest}->{connection}->prepare("insert into categories (account, name) values (?, ?)")
  or die $dbs->{source}->{connection}->errstr;

#$dsth2 = $dbs->{dest}->{connection}->prepare("
#insert into category_allocation_settings
#(allocation_setting, category, allocation, allocation_type, auto_deduct)
#values (?, (select id from categories where id is null), ?, ?, ?)")
#  or die $dbs->{source}->{connection}->errstr;

$dsth2 = $dbs->{dest}->{connection}->prepare("
insert into allocation_presets
(budget, name, category, allocation, allocation_type, auto_deduct)
values (?, 'Pay Day', (select id from categories where id = last_insert_id()), ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;

$sth = $dbs->{source}->{connection}->prepare("select * from categories where budgetname = 'bowman'") or die $dbs->{source}->{connection}->errstr;;
$sth->execute or die $sth->errstr;
while (my $row = $sth->fetchrow_hashref()) {
  next if ($row->{category} eq "All Categories");
  my $allocation_amount = $row->{which} eq "fpp"
    ? $row->{fixed_per_paycheck}
    : $row->{which} eq "ppp"
      ? $row->{percent_per_paycheck}
      : $row->{fixed_per_month};

  my $account_id = $accounts->{Checking}->{id};
  if ($row->{category} eq "Savings") { $account_id = $accounts->{Savings}->{id} }
#  elsif ($row->{category} eq "Tithing") { $account_id = $accounts->{DSavings}->{id}; $row->{deducted} = 0 }
  $dsth->execute($account_id, $row->{category})
    or die $dsth->errstr;
  print "inserted account $account_id, $row->{category}\n";

  $dsth2->execute($budget_id, $allocation_amount, ($row->{which} eq "ppp" ? "percent" : "fixed"), $row->{deducted})
     or die $dsth->errstr;
#  $dsth2->execute($allocation_setting_id, $allocation_amount, $row->{which}, $row->{deducted})
#    or die $dsth->errstr;
  print "inserted $allocation_setting_id, $allocation_amount, $row->{which}, $row->{deducted}\n";
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

$dtrans = $dbs->{dest}->{connection}->prepare("
insert into transactions (stamp, date, entity, description, reconciled, transfer)
values (?, ?, ?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;

$dalloc = $dbs->{dest}->{connection}->prepare("
insert into allocations (stamp, category, transaction, amount)
values (?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;

my ($transaction_count) = $dbs->{source}->{connection}->selectrow_array("select count(*) from transactions where budgetname = 'bowman'");
$sth = $dbs->{source}->{connection}->prepare("select * from transactions where budgetname = 'bowman' order by date, to_from, description, stamp") or die $dbs->{source}->{connection}->errstr;;
$sth->execute or die $sth->errstr;

my $transaction = undef;
my @allocations = ();

print "Inserting transactions(X) and allocations(a) (skipping beginning balance(-))\n";
my $rownum = 0;
while (my $row = $sth->fetchrow_hashref()) {
  $rownum ++;
  # skip beginning balances because we don't use those anymore
  if (($row->{amount} == 0 and $row->{date} =~ /^2003-01-01$/)
      or ($row->{date} =~ /^20[01][0-9]-01-01$/ and $row->{subcategory} eq "Beginning Balance")) {
    print "-";
    next;
  }

  # sanify nulls in to_from and description
  if ($row->{to_from} eq undef) { $row->{to_from} = "" }
  if ($row->{description} eq undef) { $row->{description} = "" }

  # evaluate if this transaction is the same as the last one.
  my $same = 0;
  if ($row->{date} eq $transaction->{date} and $transaction ne undef) {
    $same = 1 if ($row->{subcategory} =~ /^Payday|Adjustment$/
                  and $transaction->{subcategory} =~ /^Payday|Adjustment$/
                  and ($transaction->{to_from} =~ /^SOS|ArosNet|America First|Original Amount|$/
                  and $row->{to_from} eq $transaction->{to_from}));
    $same = 1 if ($transaction->{to_from} eq $row->{to_from} and $transaction->{description} eq $row->{description});
    $same = 1 if ($transaction->{description} =~ /^Discover\s+.*/i and $row->{description} =~ /^Discover\s+.*/i);
    $same = 1 if ($transaction->{description} =~ /^American Express\s+.*/i and $row->{description} =~ /^American Express\s+.*/i);
    $same = 1 if ($transaction->{description} =~ /^From ATM\s+.*/i and $row->{description} =~ /^From ATM\s+.*/i);
    $same = 1 if ($transaction->{description} =~ /^check\s*#(\d+(?:\.\d+)?).*/i and $row->{description} =~ /^check\s*#$1.*/i);
    $same = 1 if ($transaction->{description} =~ /^part\s+of\s*(\d+(?:\.\d+)?)/i and $row->{description} =~ /^part\s+of\s*$1/i);
  }

  # if this row is considered the same transaction as the last row or the last row, add it to the allocation list
  if ($same) {
    if ($row->{description} =~ /^part\s+of\s*\d+(?:\.\d+)?\s+(?:-\s*)?(.+?)$/i) {
      $transaction->{new_description} .= "; $1"
    }
    if ($row->{description} =~ /^(?:discover(?:\s+Card)?|american express)?\s+(?:-\s*)?(.+?)$/i) {
      $transaction->{new_description} .= "; $1"
    }
    push @allocations, $row;
  }
  # if this row isn't the same, we've got another transaction, process our queue now.
  else {
    if ($transaction ne undef and exists $transaction->{stamp} and length $transaction->{stamp}) {
      insert_transaction($transaction, @allocations);
    }

    $row->{new_description} = $row->{description};
    # nuke part of, as the description is no longer part of anything, it's joined.
    $row->{new_description} =~ s/^part\s+of\s*\d+(?:\.\d+)?(?:\s*\-\s*)?\s*//i;

    # next transaction to work with is the new one we just got
    $transaction = $row;
    # clear allocations for next transaction
    @allocations = ();
    # push the current row into allocations
    push @allocations, $row;
  }

  # enter final transaction on last row
  if ($rownum == $transaction_count) {
    insert_transaction($transaction, @allocations);
  }
}
print "\n";
$sth->finish;
$dtrans->finish;
$dalloc->finish;

sub insert_transaction {
  my ($transaction, @allocations) = @_;

  print "\ninserting $transaction->{new_description} $transaction->{to_from} $transaction->{subcategory}\n";
  # add the transaction
  my @params = ($transaction->{stamp},
                $transaction->{date},
                $transaction->{to_from},
                $transaction->{new_description},
                $transaction->{reconciled},
                exists $categories->{$transaction->{to_from}} ? 1 : 0);

  unless ($dtrans->execute(@params)) {
    print "transaction: " . (join ",", @params) . "\n";
    die $dtrans->errstr;
  }

  print "X";
  # the transaction id for the allocations
  my ($last_id) = $dbs->{dest}->{connection}->selectrow_array("select id from transactions where id = last_insert_id()");

  # add allocations to this transaction
  for my $allocation (@allocations) {
  # allocations
    my @params = ($allocation->{stamp}, $categories->{$allocation->{category}}->{id}, $last_id, $allocation->{amount});
    unless ($dalloc->execute(@params)) {
      print "allocation: " . (join ",", @params) . "\n";
      die $dalloc->errstr;
    }
  #my ($allocation_id) = $dbs->{dest}->{connection}->selectrow_array("select id from allocations where id is null");
   print "a";
  }
}
