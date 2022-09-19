; ModuleID = 'aplusb.c'
source_filename = "aplusb.c"
target datalayout = "e-m:o-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-apple-macosx12.0.0"

; Function Attrs: noinline nounwind optnone ssp uwtable
define void @assign(i32* %p, i32 %v) #0 {
entry:
  %p.addr = alloca i32*, align 8
  %v.addr = alloca i32, align 4
  store i32* %p, i32** %p.addr, align 8
  store i32 %v, i32* %v.addr, align 4
  %0 = load i32*, i32** %p.addr, align 8
  store i32 0, i32* %0, align 4
  br label %do.body

do.body:                                          ; preds = %do.cond, %entry
  %1 = load i32, i32* %v.addr, align 4
  %cmp = icmp slt i32 %1, 0
  br i1 %cmp, label %if.then, label %if.end

if.then:                                          ; preds = %do.body
  br label %do.end

if.end:                                           ; preds = %do.body
  %2 = load i32, i32* %v.addr, align 4
  %cmp1 = icmp eq i32 %2, 0
  br i1 %cmp1, label %if.then2, label %if.end3

if.then2:                                         ; preds = %if.end
  br label %do.cond

if.end3:                                          ; preds = %if.end
  %3 = load i32, i32* %v.addr, align 4
  %sub = sub nsw i32 %3, 1
  store i32 %sub, i32* %v.addr, align 4
  %4 = load i32*, i32** %p.addr, align 8
  %5 = load i32, i32* %4, align 4
  %add = add nsw i32 %5, 1
  store i32 %add, i32* %4, align 4
  br label %do.cond

do.cond:                                          ; preds = %if.end3, %if.then2
  %6 = load i32, i32* %v.addr, align 4
  %cmp4 = icmp sgt i32 %6, 0
  br i1 %cmp4, label %do.body, label %do.end, !llvm.loop !6

do.end:                                           ; preds = %do.cond, %if.then
  ret void
}

; Function Attrs: noinline nounwind optnone ssp uwtable
define i32 @plus(i32 %a, i32 %b) #0 {
entry:
  %a.addr = alloca i32, align 4
  %b.addr = alloca i32, align 4
  %c = alloca i32, align 4
  %d = alloca i32*, align 8
  %asgn = alloca void (i32*, i32)*, align 8
  %pasgn = alloca void (i32*, i32)**, align 8
  %i = alloca i32, align 4
  store i32 %a, i32* %a.addr, align 4
  store i32 %b, i32* %b.addr, align 4
  store i32 0, i32* %c, align 4
  store i32* %c, i32** %d, align 8
  store i32* %c, i32** %d, align 8
  %0 = load i32*, i32** %d, align 8
  %1 = load i32, i32* %0, align 4
  %inc = add nsw i32 %1, 1
  store i32 %inc, i32* %0, align 4
  store void (i32*, i32)* @assign, void (i32*, i32)** %asgn, align 8
  store void (i32*, i32)** %asgn, void (i32*, i32)*** %pasgn, align 8
  store void (i32*, i32)** %asgn, void (i32*, i32)*** %pasgn, align 8
  %2 = load void (i32*, i32)**, void (i32*, i32)*** %pasgn, align 8
  %3 = load void (i32*, i32)*, void (i32*, i32)** %2, align 8
  %4 = load i32*, i32** %d, align 8
  %5 = load i32, i32* %a.addr, align 4
  call void %3(i32* %4, i32 %5)
  %6 = load void (i32*, i32)*, void (i32*, i32)** %asgn, align 8
  %7 = load i32*, i32** %d, align 8
  %8 = load i32, i32* %a.addr, align 4
  call void %6(i32* %7, i32 %8)
  %9 = load i32, i32* %c, align 4
  %cmp = icmp eq i32 %9, 0
  br i1 %cmp, label %if.then, label %if.else

if.then:                                          ; preds = %entry
  store i32 0, i32* %i, align 4
  br label %for.cond

for.cond:                                         ; preds = %for.inc, %if.then
  %10 = load i32, i32* %i, align 4
  %cmp1 = icmp sge i32 %10, 0
  br i1 %cmp1, label %for.body, label %for.end

for.body:                                         ; preds = %for.cond
  %11 = load i32, i32* %i, align 4
  %12 = load i32, i32* %b.addr, align 4
  %cmp2 = icmp eq i32 %11, %12
  br i1 %cmp2, label %if.then3, label %if.end

if.then3:                                         ; preds = %for.body
  br label %for.end

if.end:                                           ; preds = %for.body
  %13 = load i32, i32* %c, align 4
  %inc4 = add nsw i32 %13, 1
  store i32 %inc4, i32* %c, align 4
  br label %for.inc

for.inc:                                          ; preds = %if.end
  %14 = load i32, i32* %i, align 4
  %inc5 = add nsw i32 %14, 1
  store i32 %inc5, i32* %i, align 4
  br label %for.cond, !llvm.loop !8

for.end:                                          ; preds = %if.then3, %for.cond
  br label %if.end11

if.else:                                          ; preds = %entry
  %15 = load i32, i32* %b.addr, align 4
  %cmp6 = icmp eq i32 %15, 0
  br i1 %cmp6, label %if.then7, label %if.else8

if.then7:                                         ; preds = %if.else
  br label %if.end10

if.else8:                                         ; preds = %if.else
  br label %while.cond

while.cond:                                       ; preds = %while.body, %if.else8
  %16 = load i32, i32* %b.addr, align 4
  %cmp9 = icmp sgt i32 %16, 0
  br i1 %cmp9, label %while.body, label %while.end

while.body:                                       ; preds = %while.cond
  %17 = load i32, i32* %c, align 4
  %add = add nsw i32 %17, 1
  store i32 %add, i32* %c, align 4
  %18 = load i32, i32* %b.addr, align 4
  %dec = add nsw i32 %18, -1
  store i32 %dec, i32* %b.addr, align 4
  br label %while.cond, !llvm.loop !9

while.end:                                        ; preds = %while.cond
  br label %if.end10

if.end10:                                         ; preds = %while.end, %if.then7
  br label %if.end11

if.end11:                                         ; preds = %if.end10, %for.end
  %19 = load i32, i32* %c, align 4
  ret i32 %19
}

; Function Attrs: noinline nounwind optnone ssp uwtable
define i32 @main() #0 {
entry:
  %retval = alloca i32, align 4
  %x = alloca i32, align 4
  %y = alloca i32, align 4
  %z = alloca i32, align 4
  %c = alloca i32, align 4
  store i32 0, i32* %retval, align 4
  call void @assign(i32* %x, i32 0)
  store i32 1, i32* %y, align 4
  %0 = load i32, i32* %x, align 4
  %1 = load i32, i32* %y, align 4
  %call = call i32 @plus(i32 %0, i32 %1)
  store i32 %call, i32* %z, align 4
  store i32 1, i32* %c, align 4
  %2 = load i32, i32* %z, align 4
  ret i32 %2
}

attributes #0 = { noinline nounwind optnone ssp uwtable "darwin-stkchk-strong-link" "frame-pointer"="all" "min-legal-vector-width"="0" "no-trapping-math"="true" "probe-stack"="___chkstk_darwin" "stack-protector-buffer-size"="8" "target-cpu"="penryn" "target-features"="+cx16,+cx8,+fxsr,+mmx,+sahf,+sse,+sse2,+sse3,+sse4.1,+ssse3,+x87" "tune-cpu"="generic" }

!llvm.module.flags = !{!0, !1, !2, !3, !4}
!llvm.ident = !{!5}

!0 = !{i32 2, !"SDK Version", [2 x i32] [i32 12, i32 3]}
!1 = !{i32 1, !"wchar_size", i32 4}
!2 = !{i32 7, !"PIC Level", i32 2}
!3 = !{i32 7, !"uwtable", i32 1}
!4 = !{i32 7, !"frame-pointer", i32 2}
!5 = !{!"Apple clang version 13.1.6 (clang-1316.0.21.2.5)"}
!6 = distinct !{!6, !7}
!7 = !{!"llvm.loop.mustprogress"}
!8 = distinct !{!8, !7}
!9 = distinct !{!9, !7}
